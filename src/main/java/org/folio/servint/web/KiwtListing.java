package org.folio.servint.web;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.folio.servint.web.KiwtFilterParser.Comparison;
import org.folio.servint.web.KiwtFilterParser.Node;
import org.folio.servint.web.KiwtFilterParser.Op;
import org.folio.servint.web.KiwtFilterParser.Special;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Web-toolkit (kiwt) listing semantics shared by every collection endpoint
 * (ADR-004, REQ-022): the full legacy filter grammar (parsed by
 * {@link KiwtFilterParser}, emitted as JPA Criteria predicates with INNER
 * joins for filter paths and LEFT joins for match/sort paths), match+term
 * text search (quoted-phrase split, per-term AND, per-property OR, distinct
 * results), sort "prop;asc|desc" with bad-sort tolerance, legacy paging
 * (perPage 0/absent means 10, silent clamp to 100, max alias, 1-based page
 * over offset), and the stats envelope {results, pageSize, page, totalPages,
 * meta, totalRecords, total}. Plain mode returns the bare JSON array.
 */
@Component
public class KiwtListing {

  public static final int MAX_PER_PAGE = 100;
  public static final int DEFAULT_PER_PAGE = 10;

  private static final char LIKE_ESCAPE = '\\';

  /** Legacy getTextMatches split: whitespace, double-quoted phrases kept intact. */
  private static final Pattern TERM_SPLIT = Pattern.compile("(?!\\B\"[^\"]*)\\s+(?![^\"]*\"\\B)");

  public <T> ResponseEntity<Object> list(JpaSpecificationExecutor<T> repository,
                                         List<String> filters, List<String> match, String term,
                                         List<String> sort, Integer perPage, Integer max,
                                         Integer page, Integer offset, Boolean stats,
                                         Function<T, ?> toDto) {
    var parsedFilters = parseFilters(filters);
    int size = pageSize(perPage, max);
    int pageIndex = pageIndex(page, offset, size);
    Specification<T> spec = buildSpecification(parsedFilters, match, term, sort);
    // sorting happens inside the specification (LEFT joins + tolerance), so
    // the PageRequest stays unsorted and does not override those orders
    var result = repository.findAll(spec, PageRequest.of(pageIndex, size));
    var dtos = result.getContent().stream().map(toDto).toList();
    if (Boolean.TRUE.equals(stats)) {
      Map<String, Object> envelope = new LinkedHashMap<>();
      envelope.put("results", dtos);
      envelope.put("pageSize", size);
      envelope.put("page", pageIndex + 1);
      envelope.put("totalPages", result.getTotalPages());
      envelope.put("meta", Map.of());
      envelope.put("totalRecords", result.getTotalElements());
      envelope.put("total", result.getTotalElements());
      return ResponseEntity.ok(envelope);
    }
    return ResponseEntity.ok(dtos);
  }

  // ------------------------------------------------------------------ paging

  /** Legacy doTheLookup: perPage 0/absent falls through to max, then 10; silent clamp to 100. */
  private int pageSize(Integer perPage, Integer max) {
    Integer requested = perPage != null && perPage != 0 ? perPage
        : max != null && max != 0 ? max : null;
    int size = requested == null ? DEFAULT_PER_PAGE : requested;
    return Math.max(1, Math.min(size, MAX_PER_PAGE));
  }

  /** 1-based page takes precedence; otherwise the page derives from offset. */
  private int pageIndex(Integer page, Integer offset, int size) {
    if (page != null) {
      return Math.max(page, 1) - 1;
    }
    return offset == null ? 0 : Math.max(offset, 0) / size;
  }

  // ----------------------------------------------------------------- filters

  /** Syntactically malformed clauses are dropped, never failing the request (REQ-022 AC4). */
  private List<Node> parseFilters(List<String> filters) {
    if (filters == null) {
      return List.of();
    }
    var parsed = new ArrayList<Node>();
    for (var filter : filters) {
      if (filter == null || filter.isBlank()) {
        continue;
      }
      try {
        parsed.add(KiwtFilterParser.parse(filter));
      } catch (KiwtFilterParser.ParseException dropped) {
        // legacy ANTLR recovery: the offending clause yields no criterion
      }
    }
    return parsed;
  }

  private <T> Specification<T> buildSpecification(List<Node> filters, List<String> match,
                                                  String term, List<String> sort) {
    return (root, query, cb) -> {
      var ctx = new QueryContext(root, cb);
      var predicates = new ArrayList<Predicate>();
      for (var filter : filters) {
        try {
          predicates.add(buildNode(filter, ctx));
        } catch (DroppedClauseException dropped) {
          // whole-parameter drop: top-level empty right side (AC1) or an
          // uncoercible value, alone or inside a compound (AC4) — compounds
          // with empty-RHS leaves never reach here, the parser absorbs them
          // into a single comparison first (AC6, D-32)
        }
      }
      var textPredicate = buildTextMatch(match, term, ctx);
      if (textPredicate != null) {
        predicates.add(textPredicate);
        query.distinct(true); // legacy projects distinct ids
      }
      applySort(sort, ctx, query);
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private Predicate buildNode(Node node, QueryContext ctx) {
    return switch (node) {
      case KiwtFilterParser.And and -> ctx.cb.and(buildAll(and.terms(), ctx));
      case KiwtFilterParser.Or or -> ctx.cb.or(buildAll(or.terms(), ctx));
      case KiwtFilterParser.Not not -> ctx.cb.not(buildNode(not.term(), ctx));
      case Comparison comparison -> buildComparison(comparison, ctx);
      case Special special -> buildSpecial(special, ctx);
      case KiwtFilterParser.Range range -> ctx.cb.and(
          buildOrientedComparison(range.subject(), invert(range.lowOp()), range.low(), ctx),
          buildOrientedComparison(range.subject(), range.highOp(), range.high(), ctx));
    };
  }

  private Predicate[] buildAll(List<Node> nodes, QueryContext ctx) {
    var predicates = new Predicate[nodes.size()];
    for (int i = 0; i < nodes.size(); i++) {
      predicates[i] = buildNode(nodes.get(i), ctx);
    }
    return predicates;
  }

  /** Legacy ambiguous-subject rule: try LHS as the property, else RHS with the operator inverted. */
  private Predicate buildComparison(Comparison comparison, QueryContext ctx) {
    if (!comparison.lhs().isEmpty() && comparison.rhs().isEmpty()) {
      // empty right side: dropped before property validation, so an unknown
      // property with an empty right side answers 200 unfiltered (REQ-022 AC1)
      throw new DroppedClauseException();
    }
    var entity = ctx.root.getModel();
    if (probeType(entity, comparison.lhs()) != null) {
      return buildOrientedComparison(comparison.lhs(), comparison.op(), comparison.rhs(), ctx);
    }
    if (probeType(entity, comparison.rhs()) != null) {
      return buildOrientedComparison(comparison.rhs(), invert(comparison.op()), comparison.lhs(), ctx);
    }
    throw new InvalidKiwtPropertyException(comparison.lhs());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Predicate buildOrientedComparison(String subject, Op op, String value, QueryContext ctx) {
    if (value.isEmpty()) {
      // empty right side → dropped before property validation and join
      // planning, so no abandoned INNER join can filter null-associated rows
      // (REQ-022 AC1; evidence docs/migration/evidence/r13-legacy/
      // run3-r16-empty-rhs + r22-legacy-compound)
      throw new DroppedClauseException();
    }
    if (probeType(ctx.root.getModel(), subject) == null) {
      throw new InvalidKiwtPropertyException(subject);
    }
    var path = resolvePath(ctx, subject, JoinType.INNER);
    var type = path.getJavaType();
    var cb = ctx.cb;
    return switch (op) {
      case EQ -> cb.equal(path, coerce(type, value));
      case NEQ -> cb.notEqual(path, coerce(type, value));
      case GT -> cb.greaterThan((Expression) path, comparableValue(type, value));
      case GE -> cb.greaterThanOrEqualTo((Expression) path, comparableValue(type, value));
      case LT -> cb.lessThan((Expression) path, comparableValue(type, value));
      case LE -> cb.lessThanOrEqualTo((Expression) path, comparableValue(type, value));
      case CONT -> containsPredicate(path, type, value, cb, false);
      case NCONT -> containsPredicate(path, type, value, cb, true);
      // =i= is legacy ilike WITHOUT the contains wrap: the same broken-%
      // transform applies, _ stays a live single-char wildcard and \% / \_
      // are escaped literals (r32 oracle mi1-mi4)
      case CIEQ -> type == String.class
          ? cb.like(cb.lower(path.as(String.class)),
              legacyIlikeValue(value).toLowerCase(Locale.ROOT), LIKE_ESCAPE)
          : cb.equal(path, coerce(type, value));
    };
  }

  private static Op invert(Op op) {
    return switch (op) {
      case GT -> Op.LT;
      case GE -> Op.LE;
      case LT -> Op.GT;
      case LE -> Op.GE;
      default -> op;
    };
  }

  @SuppressWarnings("rawtypes")
  private Comparable comparableValue(Class<?> type, String value) {
    if (value.isEmpty() || !Comparable.class.isAssignableFrom(type)) {
      throw new DroppedClauseException();
    }
    return (Comparable) coerce(type, value);
  }

  /** {@code =~} / {@code !~}: ci-contains for strings; legacy falls back to (not-)equals otherwise. */
  private Predicate containsPredicate(Path<?> path, Class<?> type, String value,
                                      CriteriaBuilder cb, boolean negated) {
    if (type == String.class) {
      var like = cb.like(cb.lower(path.as(String.class)),
          "%" + legacyIlikeValue(value).toLowerCase(Locale.ROOT) + "%", LIKE_ESCAPE);
      return negated ? cb.not(like) : like;
    }
    return negated ? cb.notEqual(path, coerce(type, value)) : cb.equal(path, coerce(type, value));
  }

  private Predicate buildSpecial(Special special, QueryContext ctx) {
    var attribute = probeAttribute(ctx.root.getModel(), special.subject());
    if (attribute == null) {
      throw new InvalidKiwtPropertyException(special.subject());
    }
    var cb = ctx.cb;
    return switch (special.kind()) {
      // legacy maps "is set" to isNotNull and "is not set" to NOT(isNotNull)
      case IS_NULL, IS_NOT_SET -> cb.isNull(resolvePath(ctx, special.subject(), JoinType.INNER));
      case IS_NOT_NULL, IS_SET -> cb.isNotNull(resolvePath(ctx, special.subject(), JoinType.INNER));
      case IS_EMPTY -> cb.isEmpty(collectionPath(ctx, special.subject(), attribute));
      case IS_NOT_EMPTY -> cb.isNotEmpty(collectionPath(ctx, special.subject(), attribute));
    };
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Expression<java.util.Collection<Object>> collectionPath(QueryContext ctx, String subject,
                                                                  Attribute<?, ?> attribute) {
    if (!(attribute instanceof PluralAttribute)) {
      throw new DroppedClauseException(); // is [not] empty applies to collections only
    }
    return (Expression) resolvePath(ctx, subject, JoinType.INNER);
  }

  // -------------------------------------------------------------- match/term

  /**
   * Legacy getTextMatches (REQ-022 AC5): per-term AND within a property,
   * OR across match properties, unknown properties skipped.
   */
  private Predicate buildTextMatch(List<String> match, String term, QueryContext ctx) {
    if (term == null || term.isBlank() || match == null || match.isEmpty()) {
      return null;
    }
    var terms = Arrays.stream(TERM_SPLIT.split(term.trim()))
        .map(t -> t.replace("\"", ""))
        .filter(t -> !t.isEmpty())
        .toList();
    if (terms.isEmpty()) {
      return null;
    }
    var cb = ctx.cb;
    var perProperty = new ArrayList<Predicate>();
    for (var property : match) {
      var type = probeType(ctx.root.getModel(), property);
      if (type == null) {
        continue; // unknown match property skipped (REQ-022 AC4)
      }
      try {
        var path = resolvePath(ctx, property, JoinType.LEFT);
        var perTerm = new ArrayList<Predicate>();
        for (var t : terms) {
          // legacy getTextMatches passes terms into ilike UNTRANSFORMED:
          // % and _ are live wildcards, \% and \_ escaped literals
          // (r32 oracle t1-t5/mp8/d12)
          perTerm.add(type == String.class
              ? cb.like(cb.lower(path.as(String.class)),
                  "%" + t.toLowerCase(Locale.ROOT) + "%", LIKE_ESCAPE)
              : cb.equal(path, coerce(type, t)));
        }
        perProperty.add(cb.and(perTerm.toArray(new Predicate[0])));
      } catch (DroppedClauseException skipped) {
        // non-string match property whose term can't coerce — skipped
      }
    }
    return perProperty.isEmpty() ? null : cb.or(perProperty.toArray(new Predicate[0]));
  }

  // -------------------------------------------------------------------- sort

  /**
   * Sorting inside the Specification: LEFT joins, unknown properties skipped
   * (legacy addSorts tolerance). The count query (result type Long) must not
   * carry ORDER BY.
   */
  private void applySort(List<String> sort, QueryContext ctx, CriteriaQuery<?> query) {
    var resultType = query.getResultType();
    if (resultType == Long.class || resultType == long.class) {
      return;
    }
    var orders = new ArrayList<Order>();
    if (sort != null) {
      for (var expression : sort) {
        if (expression == null || expression.isBlank()) {
          continue;
        }
        var parts = expression.split(";", 2);
        var property = parts[0].trim();
        if (probeType(ctx.root.getModel(), property) == null) {
          continue; // unknown/non-sortable sort property skipped, request answers 200
        }
        var path = resolvePath(ctx, property, JoinType.LEFT);
        var descending = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());
        orders.add(descending ? ctx.cb.desc(path) : ctx.cb.asc(path));
      }
    }
    if (orders.isEmpty()) {
      orders.add(ctx.cb.asc(ctx.root.get("id")));
    }
    query.orderBy(orders);
  }

  // ------------------------------------------------------- paths & coercion

  /** Per-query state: join reuse keyed by (join type, dotted path prefix). */
  private static final class QueryContext {
    final Root<?> root;
    final CriteriaBuilder cb;
    final Map<String, From<?, ?>> joins = new HashMap<>();

    QueryContext(Root<?> root, CriteriaBuilder cb) {
      this.root = root;
      this.cb = cb;
    }
  }

  /** Chains joins for every intermediate hop: INNER for filters, LEFT for match/sort (legacy). */
  private static Path<?> resolvePath(QueryContext ctx, String dottedPath, JoinType joinType) {
    var parts = dottedPath.split("\\.");
    From<?, ?> from = ctx.root;
    var prefix = new StringBuilder(joinType.name());
    for (int i = 0; i < parts.length - 1; i++) {
      prefix.append('.').append(parts[i]);
      var key = prefix.toString();
      var joined = ctx.joins.get(key);
      if (joined == null) {
        joined = from.join(parts[i], joinType);
        ctx.joins.put(key, joined);
      }
      from = joined;
    }
    return from.get(parts[parts.length - 1]);
  }

  /** Walks the metamodel along a dotted path; null when any segment is unknown. */
  private static Attribute<?, ?> probeAttribute(ManagedType<?> entity, String dottedPath) {
    if (dottedPath == null || dottedPath.isEmpty()) {
      return null;
    }
    var current = entity;
    var parts = dottedPath.split("\\.", -1);
    for (int i = 0; i < parts.length; i++) {
      Attribute<?, ?> attribute;
      try {
        attribute = current.getAttribute(parts[i]);
      } catch (IllegalArgumentException unknown) {
        return null;
      }
      if (i == parts.length - 1) {
        return attribute;
      }
      var target = attribute instanceof PluralAttribute<?, ?, ?> plural
          ? plural.getElementType() : ((SingularAttribute<?, ?>) attribute).getType();
      if (!(target instanceof ManagedType<?> managed)) {
        return null;
      }
      current = managed;
    }
    return null;
  }

  /** The Java type driving value coercion (element type for collection attributes). */
  private static Class<?> probeType(ManagedType<?> entity, String dottedPath) {
    var attribute = probeAttribute(entity, dottedPath);
    if (attribute == null) {
      return null;
    }
    return attribute instanceof PluralAttribute<?, ?, ?> plural
        ? plural.getElementType().getJavaType() : attribute.getJavaType();
  }

  /**
   * Per-type coercers keyed by the JPA attribute's Java type. Strings pass
   * through verbatim (no trim); every other target trims first. Boxed and
   * primitive class literals map to the same coercer. Any target type absent
   * here is unsupported (e.g. association equality) and drops the clause.
   */
  private static final Map<Class<?>, Function<String, Object>> COERCERS = Map.ofEntries(
      Map.entry(String.class, raw -> raw),
      Map.entry(Boolean.class, raw -> Boolean.valueOf(raw.trim())),
      Map.entry(boolean.class, raw -> Boolean.valueOf(raw.trim())),
      Map.entry(Long.class, raw -> Long.valueOf(raw.trim())),
      Map.entry(long.class, raw -> Long.valueOf(raw.trim())),
      Map.entry(Integer.class, raw -> Integer.valueOf(raw.trim())),
      Map.entry(int.class, raw -> Integer.valueOf(raw.trim())),
      Map.entry(UUID.class, raw -> UUID.fromString(raw.trim())),
      Map.entry(Instant.class, raw -> Instant.parse(raw.trim())),
      Map.entry(LocalDate.class, raw -> LocalDate.parse(raw.trim())),
      Map.entry(LocalDateTime.class, raw -> LocalDateTime.parse(raw.trim())));

  /**
   * Coerces the raw filter value to the JPA attribute's Java type; failure
   * (or an unsupported/unresolved target type) drops the clause per REQ-022 AC4.
   */
  static Object coerce(Class<?> type, String raw) {
    var coercer = type == null ? null : COERCERS.get(type);
    if (coercer == null) {
      throw new DroppedClauseException(); // unsupported target type (e.g. association equality)
    }
    try {
      return coercer.apply(raw);
    } catch (RuntimeException notCoercible) {
      throw new DroppedClauseException();
    }
  }

  /**
   * Legacy SimpleLookupService's ilike value pipeline, bug for bug (DP-1(a);
   * r32 oracle mp1-mp8): every {@code %} with a preceding non-backslash
   * character becomes the LITERAL two characters {@code $2} — a botched
   * backreference legacy ships (consuming regex, left to right, proven by
   * the planted-row bind {@code %ab$2cd%}). A leading {@code %} survives as
   * a live wildcard, {@code _} is always live, and backslashes flow through
   * to the pattern so {@code \%}/{@code \_} are escaped literals via
   * LIKE ESCAPE.
   */
  private static String legacyIlikeValue(String value) {
    return value.replaceAll("([^\\\\])%", "$1\\$2");
  }

  /** Internal signal: this clause can't apply to the typed property — dropped, request answers 200. */
  static final class DroppedClauseException extends RuntimeException {
  }
}
