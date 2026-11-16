package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.SupportedLocale;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

/**
 * Spring Data fragment of {@link PostTranslationRepository}. The JPQL is put
 * together from fixed pieces only; request values are always bound.
 */
class VisiblePostQueriesImpl implements VisiblePostQueries {

  private static final String VISIBLE =
    "p.deletedAt IS NULL AND t.status = dev.olekshyr.api.blog.PostStatus.PUBLISHED";

  private final EntityManager em;

  VisiblePostQueriesImpl(EntityManager em) {
    this.em = em;
  }

  @Override
  public Page<Long> findVisiblePostIds(
    @Nullable SupportedLocale locale,
    @Nullable String tag,
    PostOrder order,
    Pageable pageable
  ) {
    StringBuilder from = new StringBuilder("FROM Post p JOIN p.translations t");
    if (tag != null) {
      from.append(" JOIN p.tags tag");
    }
    from.append(" WHERE ").append(VISIBLE);
    if (locale != null) {
      from.append(" AND t.locale = :locale");
    }
    if (tag != null) {
      from.append(" AND tag.slug = :tag");
    }
    String direction = order == PostOrder.NEWEST ? "DESC" : "ASC";

    TypedQuery<Long> ids = em.createQuery(
      "SELECT p.id " +
      from +
      " GROUP BY p.id, p.slug ORDER BY MIN(t.publishedAt) " +
      direction +
      ", p.slug " +
      direction,
      Long.class
    );
    TypedQuery<Long> count = em.createQuery(
      "SELECT COUNT(DISTINCT p.id) " + from,
      Long.class
    );
    bind(ids, locale, tag);
    bind(count, locale, tag);
    ids.setFirstResult((int) pageable.getOffset());
    ids.setMaxResults(pageable.getPageSize());

    return PageableExecutionUtils.getPage(
      ids.getResultList(),
      pageable,
      count::getSingleResult
    );
  }

  @Override
  public Map<Long, Long> countVisiblePostsPerTag(
    @Nullable SupportedLocale locale
  ) {
    String jpql =
      "SELECT tag.id, COUNT(DISTINCT p.id) " +
      "FROM Post p JOIN p.tags tag JOIN p.translations t WHERE " +
      VISIBLE +
      (locale != null ? " AND t.locale = :locale" : "") +
      " GROUP BY tag.id";
    TypedQuery<Object[]> query = em.createQuery(jpql, Object[].class);
    bind(query, locale, null);

    List<Object[]> rows = query.getResultList();
    Map<Long, Long> counts = new HashMap<>();
    rows.forEach(row -> counts.put((Long) row[0], (Long) row[1]));
    return counts;
  }

  private static void bind(
    Query query,
    @Nullable SupportedLocale locale,
    @Nullable String tag
  ) {
    if (locale != null) {
      query.setParameter("locale", locale);
    }
    if (tag != null) {
      query.setParameter("tag", tag);
    }
  }
}
