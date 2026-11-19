package com.eyalm.adns.data.nextdns.analytics

/** The list after a page was added, and the cursor to ask for next, if there should be a next. */
data class AnalyticsPageMerge(
    val rows: List<StatRow>,
    val nextCursor: String?,
)

/**
 * Adds one page of an analytics ranking (for example resolved domains) to what is already shown.
 *
 * - A row that is identical to one already shown, in every field, is not added again. Rows that
 *   differ in any way, including the same domain with another count, are all kept.
 * - Paging ends when the API gives no way forward: an empty page, a page that only repeats rows
 *   already shown, or a cursor equal to the one just used. Without this a cursor that does not
 *   advance would be requested again and again, appending the same page each time.
 */
fun mergeAnalyticsPage(
    shown: List<StatRow>,
    page: List<StatRow>,
    requestedCursor: String?,
    returnedCursor: String?,
): AnalyticsPageMerge {
    val seen = HashSet<StatRow>(shown)
    val added = page.filter { seen.add(it) }
    val noWayForward = page.isEmpty() ||
        added.isEmpty() ||
        (returnedCursor != null && returnedCursor == requestedCursor)
    return AnalyticsPageMerge(
        rows = shown + added,
        nextCursor = if (noWayForward) null else returnedCursor,
    )
}
