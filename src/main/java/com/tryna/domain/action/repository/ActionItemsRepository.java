package com.tryna.domain.action.repository;

import com.tryna.domain.action.entity.ActionItems;
import com.tryna.domain.action.enums.ItemType;
import com.tryna.domain.event.enums.EventStatus;
import com.tryna.domain.event.enums.SourceType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ActionItemsRepository extends JpaRepository<ActionItems, Long> {

    // ?④굔 以鍮???ぉ(???? ??젣??(ActionItem ?꾨찓?몄뿉???ъ슜)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ActionItems a
               SET a.deletedAt = :deletedAt
             WHERE a.actionItemId = :actionItemId
               AND a.deletedAt IS NULL
            """)
    int softDeleteById(
            @Param("actionItemId") Long actionItemId,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ActionItems a
               SET a.deletedAt = :deletedAt
             WHERE a.parentEvent.eventId = :eventId
               AND a.deletedAt IS NULL
            """)
    int softDeleteByParentEventId(
            @Param("eventId") Long eventId,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ActionItems a
               SET a.deletedAt = :deletedAt
             WHERE a.parentEvent.eventId = :eventId
               AND a.actionItemId IN :actionItemIds
               AND a.deletedAt IS NULL
            """)
    int softDeleteByParentEventIdAndActionItemIdIn(
            @Param("eventId") Long eventId,
            @Param("actionItemIds") Collection<Long> actionItemIds,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ActionItems a
               SET a.deletedAt = :deletedAt
             WHERE a.parentEvent.eventId = :eventId
               AND a.displayDate = :displayDate
               AND a.deletedAt IS NULL
            """)
    int softDeleteByParentEventIdAndDisplayDate(
            @Param("eventId") Long eventId,
            @Param("displayDate") LocalDate displayDate,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ActionItems a
               SET a.deletedAt = :deletedAt
             WHERE a.parentEvent.eventId = :eventId
               AND a.displayDate >= :displayDate
               AND a.deletedAt IS NULL
            """)
    int softDeleteByParentEventIdFromDisplayDate(
            @Param("eventId") Long eventId,
            @Param("displayDate") LocalDate displayDate,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    /**
     * ??젣?섏? ?딆? 以鍮??ㅽ뻾 ??ぉ??ID濡?議고쉶?⑸땲??
     *
     * @param actionItemId 以鍮??ㅽ뻾 ??ぉ ID
     * @return ??젣?섏? ?딆? 以鍮??ㅽ뻾 ??ぉ
     */
    Optional<ActionItems> findByActionItemIdAndDeletedAtIsNull(
            Long actionItemId
    );

    /**
     * ?뱀젙 諛섎났 ?쇱젙?먯꽌 湲곗? ?뚯감 ?댄썑??以鍮??ㅽ뻾 ??ぉ??議고쉶?⑸땲??
     *
     * THIS_AND_FUTURE ?섏젙 ??湲곗? ?뚯감 ?댄썑????ぉ??
     * ??諛섎났 ?쒕━利덈줈 蹂듭궗?섍린 ?꾪빐 ?ъ슜?⑸땲??
     */
    List<ActionItems>
    findAllByParentEvent_EventIdAndOccurrenceDateGreaterThanEqualAndDeletedAtIsNullOrderByOccurrenceDateAscActionItemIdAsc(
            Long eventId,
            LocalDate occurrenceDate
    );

    /**
     * ?뱀젙 ?쇱젙???곌껐????젣?섏? ?딆? 以鍮??ㅽ뻾 ??ぉ??議고쉶?⑸땲??
     *
     * ?쇱젙 ?곸꽭 ?붾㈃?먯꽌 ?쇱젙???쒖꽌濡??쒖떆?????덈룄濡?
     * ?쒖떆 ?좎쭨, ?쒖떆 ?쇱떆, ??ぉ ID ?쒖쑝濡??뺣젹?⑸땲??
     *
     * @param eventId ?쇱젙 ID
     * @return ?쇱젙???곌껐??以鍮??ㅽ뻾 ??ぉ 紐⑸줉
     */
    @EntityGraph(attributePaths = "parentEvent")
    List<ActionItems>
    findAllByParentEvent_EventIdAndDeletedAtIsNullOrderByDisplayDateAscDisplayDatetimeAscActionItemIdAsc(
            Long eventId
    );

    /**
     * ?뱀젙 ?쇱젙???곌껐????젣?섏? ?딆? 以鍮??ㅽ뻾 ??ぉ??議고쉶?⑸땲??
     *
     * ?쇱젙 ?곸꽭 ?붾㈃?먯꽌 ?쇱젙???쒖꽌濡??쒖떆?????덈룄濡?
     * ?쒖떆 ?좎쭨, ?쒖떆 ?쇱떆, ??ぉ ID ?쒖쑝濡??뺣젹?⑸땲??
     *
     * @param eventId ?쇱젙 ID
     * @param occurrenceDate 諛섎났 ?뚯감 ?뚯냽 ?좎쭨
     * @return ?쇱젙???곌껐??以鍮??ㅽ뻾 ??ぉ 紐⑸줉
     */
    @EntityGraph(attributePaths = "parentEvent")
    List<ActionItems>
    findAllByParentEvent_EventIdAndOccurrenceDateAndDeletedAtIsNullOrderByDisplayDateAscDisplayDatetimeAscActionItemIdAsc(
            Long eventId,
            LocalDate occurrenceDate
    );

    List<ActionItems> findAllByParentEvent_EventIdAndDeletedAtIsNull(
            Long eventId
    );

    /**
     * ?꾩옱 ?ъ슜?먯쓽 ?쇱젙???곌껐????ぉ 以?
     * ?좏깮???좎쭨???쒖떆???쒓컙???ㅽ뻾 ??ぉ??議고쉶?⑸땲??
     *
     * @param userId ?꾩옱 ?ъ슜??ID
     * @param date 議고쉶 ?좎쭨
     * @param itemType ??ぉ ?좏삎
     * @return ?쒓컙???ㅽ뻾 ??ぉ 紐⑸줉
     */
    @Query("""
            SELECT a
              FROM ActionItems a
              JOIN FETCH a.parentEvent e
              JOIN UserEvents ue ON ue.event = e
             WHERE ue.user.userId = :userId
               AND a.displayDate = :date
               AND a.itemType = :itemType
               AND (e.isRecurring = false OR a.offsetDays IS NULL)
               AND a.deletedAt IS NULL
             ORDER BY a.displayDatetime ASC, a.actionItemId ASC
            """)
    List<ActionItems> findCalendarActionItemsByDate(
            @Param("userId") Long userId,
            @Param("date") LocalDate date,
            @Param("itemType") ItemType itemType
    );
    @Query("""
            SELECT a
              FROM ActionItems a
              JOIN FETCH a.parentEvent e
              JOIN UserEvents ue ON ue.event = e
             WHERE ue.user.userId = :userId
               AND e.isRecurring = true
               AND a.itemType = :itemType
               AND a.offsetDays IS NOT NULL
               AND a.deletedAt IS NULL
             ORDER BY a.actionItemId ASC
            """)
    List<ActionItems> findRecurringTimedActionItemsByUserId(
            @Param("userId") Long userId,
            @Param("itemType") ItemType itemType
    );

    // ?뚯썝 ?덊눜 ???꾩껜 以鍮???ぉ 踰뚰겕 ??젣??(User ?꾨찓???덊눜 濡쒖쭅?먯꽌 ?ъ슜)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ActionItems a
               SET a.deletedAt = :deletedAt
             WHERE a.parentEvent.eventId IN (
                   SELECT ue.event.eventId
                     FROM UserEvents ue
                    WHERE ue.user.userId = :userId
                      AND ue.eventRole = 'OWNER'
             )
               AND a.deletedAt IS NULL
            """)
    int softDeleteByUserId(
            @Param("userId") Long userId,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    /**
     * ?꾩옱 ?ъ슜?먯쓽 Tryna ?대? ?쇱젙???곌껐??以鍮??ㅽ뻾 ??ぉ 以?
     * ?쒕ぉ??寃?됱뼱媛 ?ы븿????ぉ??議고쉶?⑸땲??
     *
     * ??젣????ぉ, ?몃? 罹섎┛???쇱젙???곌껐????ぉ,
     * 寃??寃곌낵???몄텧?섏? ?딅뒗 ?곹깭???쇱젙???곌껐????ぉ? ?쒖쇅?⑸땲??
     *
     * @param userId ?꾩옱 ?ъ슜??ID
     * @param keyword 寃???ㅼ썙??
     * @param eventStatuses 寃??媛?ν븳 ?쇱젙 ?곹깭
     * @return ?쒕ぉ??寃?됱뼱? ?쇱튂??以鍮??ㅽ뻾 ??ぉ 紐⑸줉
     */
    @Query("""
            SELECT a
              FROM ActionItems a
              JOIN FETCH a.parentEvent e
              JOIN UserEvents ue ON ue.event = e
             WHERE ue.user.userId = :userId
               AND e.eventStatus IN :eventStatuses
               AND a.deletedAt IS NULL
               AND LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
             ORDER BY a.actionItemId ASC
            """)
    List<ActionItems> findSearchMatchesByUserIdAndKeyword(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("eventStatuses") Collection<EventStatus> eventStatuses
    );

    /**
     * 諛섎났 ?쇱젙???뱀젙 ?뚯감???랁븳 以鍮??ㅽ뻾 ??ぉ??soft delete ?⑸땲??
     *
     * SINGLE ?쇱젙 ?섏젙 ???먮낯 ?뚯감????ぉ??
     * 以묐났 ?몄텧?섏? ?딅룄濡?泥섎━?섍린 ?꾪빐 ?ъ슜?⑸땲??
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE ActionItems a
           SET a.deletedAt = :deletedAt
         WHERE a.parentEvent.eventId = :eventId
           AND a.occurrenceDate = :occurrenceDate
           AND a.deletedAt IS NULL
        """)
    int softDeleteByParentEventIdAndOccurrenceDate(
            @Param("eventId") Long eventId,
            @Param("occurrenceDate") LocalDate occurrenceDate,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    /**
     * 諛섎났 ?쇱젙??湲곗? ?뚯감 ?댄썑 以鍮??ㅽ뻾 ??ぉ??紐⑤몢 soft delete ?⑸땲??
     *
     * THIS_AND_FUTURE ?쇱젙 ?섏젙 ??湲곗〈 ?쒕━利덉쓽 ?댄썑 ?뚯감 ??ぉ??
     * ???쒕━利덉쓽 蹂듭궗 ??ぉ怨?以묐났?섏? ?딅룄濡?泥섎━?⑸땲??
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE ActionItems a
           SET a.deletedAt = :deletedAt
         WHERE a.parentEvent.eventId = :eventId
           AND a.occurrenceDate >= :occurrenceDate
           AND a.deletedAt IS NULL
        """)
    int softDeleteByParentEventIdFromOccurrenceDate(
            @Param("eventId") Long eventId,
            @Param("occurrenceDate") LocalDate occurrenceDate,
            @Param("deletedAt") LocalDateTime deletedAt
    );
}

