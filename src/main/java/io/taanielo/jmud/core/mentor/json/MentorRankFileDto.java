package io.taanielo.jmud.core.mentor.json;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * JSON transfer object for the Mentors' Guild rank ladder file ({@code data/mentor/ranks.json}).
 *
 * @param schemaVersion the mentor-rank schema version
 * @param ranks         the rank rungs in ascending order of {@code menteesRequired}
 */
public record MentorRankFileDto(int schemaVersion, @Nullable List<MentorRankDto> ranks) {

    /**
     * JSON transfer object for a single mentor rank entry.
     *
     * @param menteesRequired      lifetime graduated mentees needed to reach this rank
     * @param title                the unique milestone title granted at this rank
     * @param mentorXpBonusPercent the mentor's own bonus-XP percentage while actively mentoring
     */
    public record MentorRankDto(
        @Nullable Integer menteesRequired,
        @Nullable String title,
        @Nullable Integer mentorXpBonusPercent) {
    }
}
