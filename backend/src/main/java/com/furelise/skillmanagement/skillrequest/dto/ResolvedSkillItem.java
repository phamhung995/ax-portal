package com.furelise.skillmanagement.skillrequest.dto;

/** A skill linked in a resolved comment — surfaced on the request post. */
public record ResolvedSkillItem(
        Long skillId,
        String skillName,
        String skillStatus,
        /** Name of the user who wrote the resolved comment. */
        String commenterName,
        /** Knox ID of the user who wrote the resolved comment. */
        String commenterKnoxId
) {
}
