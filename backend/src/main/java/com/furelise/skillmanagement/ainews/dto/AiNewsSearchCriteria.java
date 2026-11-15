package com.furelise.skillmanagement.ainews.dto;

import java.time.LocalDate;

/**
 * Optional advanced-search criteria for the AI News list endpoint.
 * All fields are nullable/blank = "no filter on this field". When none are
 * set the query behaves exactly like the plain {@code limit}-only list.
 *
 * <p>{@code from}/{@code to} are calendar dates (inclusive of the whole
 * {@code to} day — the service converts them to a [fromStart, toStart+1day)
 * instant range).</p>
 */
public record AiNewsSearchCriteria(
        String title,
        String source,
        String sender,
        LocalDate from,
        LocalDate to
) {
}
