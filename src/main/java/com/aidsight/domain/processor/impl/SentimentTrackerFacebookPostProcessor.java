package com.aidsight.domain.processor.impl;

import com.aidsight.domain.model.instance.FacebookPostInstance;
import com.aidsight.domain.model.task.SentimentTrackerTask;
import com.aidsight.domain.processor.Processor;
import com.aidsight.infrastructure.external.python.SentimentAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Processor implementation for sentiment tracking tasks on Facebook post instances.
 * <p>
 * This processor analyzes the sentiment of Facebook posts using two sources:
 * comments (analyzed using a Python-based sentiment analysis service) and reactions
 * (analyzed based on weighted sentiment values for each reaction type).
 * </p>
 * <p>
 * The final sentiment score is a weighted combination of:
 * <ul>
 *   <li>Comment sentiment (60% weight): Average sentiment across all valid comments</li>
 *   <li>Reaction sentiment (40% weight): Weighted average based on reaction types</li>
 * </ul>
 * </p>
 * <p>
 * Reaction weights range from -1.0 (most negative) to 1.0 (most positive):
 * <ul>
 *   <li>LOVE: 1.0 (most positive)</li>
 *   <li>CARE: 0.8 (very positive)</li>
 *   <li>LIKE: 0.6 (positive)</li>
 *   <li>HAHA: 0.5 (mildly positive)</li>
 *   <li>WOW: 0.3 (neutral-positive)</li>
 *   <li>SAD: -0.7 (negative)</li>
 *   <li>ANGRY: -1.0 (most negative)</li>
 * </ul>
 * </p>
 * <p>
 * Posts with neither comments nor reactions are excluded from the results.
 * Invalid or empty comments are skipped during analysis.
 * </p>
 *
 * @see SentimentTrackerTask
 * @see FacebookPostInstance
 * @see SentimentAnalyzer
 */
public class SentimentTrackerFacebookPostProcessor implements Processor<SentimentTrackerTask, FacebookPostInstance, SentimentTrackerTask.Result> {
    private static final Logger logger = LoggerFactory.getLogger(SentimentTrackerFacebookPostProcessor.class);

    // Sentiment weights for each reaction type (range: -1.0 to 1.0)
    private static final float REACTION_WEIGHT_LIKE = 0.1f;
    private static final float REACTION_WEIGHT_LOVE = 0.9f;
    private static final float REACTION_WEIGHT_CARE = 1.0f;
    private static final float REACTION_WEIGHT_HAHA = 0.5f;
    private static final float REACTION_WEIGHT_WOW = 0.3f;
    private static final float REACTION_WEIGHT_SAD = -0.8f;
    private static final float REACTION_WEIGHT_ANGRY = -1.0f;

    // Weight distribution between reactions and comments (must sum to 1.0)
    private static final float REACTIONS_CONTRIBUTION = 0.4f;  // 40% from reactions
    private static final float COMMENTS_CONTRIBUTION = 0.6f;   // 60% from comments

    /**
     * Processes a sentiment tracking task on a Facebook post instance.
     * <p>
     * This method analyzes both comments and reactions to calculate a combined
     * sentiment score. Comment sentiment is weighted at 60% and reaction sentiment
     * at 40%. If only one source is available, it uses that source exclusively.
     * The final score is associated with the post's publication date.
     * </p>
     * <p>
     * Posts without both comments and reactions return an empty result.
     * </p>
     *
     * @param task the sentiment tracking task to execute
     * @param instance the Facebook post instance to analyze
     * @return a sentiment tracking result containing the combined sentiment score by date
     * @throws RuntimeException if the sentiment analysis process crashes or fails
     */
    @Override
    public SentimentTrackerTask.Result process(SentimentTrackerTask task, FacebookPostInstance instance) {
        SentimentTrackerTask.Result result = new SentimentTrackerTask.Result();

        List<String> comments = instance.getComments();
        int[] reactions = instance.getReactionsCount();

        // Skip posts with no comments and no reactions
        boolean hasComments = comments != null && !comments.isEmpty();
        boolean hasReactions = reactions != null && reactions.length > 0;

        if (!hasComments && !hasReactions) {
            return result;
        }

        try {
            float commentSentiment = 0f;
            float reactionSentiment = 0f;

            // Calculate reaction sentiment
            if (hasReactions) {
                reactionSentiment = calculateReactionSentiment(reactions);
                logger.debug("Post {} reaction sentiment: {}", instance.getId(), reactionSentiment);
            }

            // Calculate comment sentiment
            if (hasComments) {
                SentimentAnalyzer analyzer = SentimentAnalyzer.getInstance();

                int totalScore = 0;
                int validComments = 0;

                // Analyze each comment
                for (String comment : comments) {
                    if (comment == null || comment.trim().isEmpty()) {
                        continue;
                    }

                    try {
                        int score = analyzer.analyzeSentiment(comment);
                        totalScore += score;
                        validComments++;
                    } catch (IllegalArgumentException e) {
                        logger.warn("Skipping invalid comment: {}", e.getMessage());
                    } catch (IOException e) {
                        logger.error("Error analyzing comment sentiment", e);
                        throw new RuntimeException("Failed to analyze sentiment", e);
                    }
                }

                if (validComments > 0) {
                    commentSentiment = (float) totalScore / validComments;
                    logger.debug("Post {} comment sentiment: {} (from {} comments)",
                               instance.getId(), commentSentiment, validComments);
                }
            }

            // Combine sentiments with weighted average
            float finalSentiment;
            if (hasComments && hasReactions) {
                // Both sources available: use weighted combination
                finalSentiment = (commentSentiment * COMMENTS_CONTRIBUTION) +
                                (reactionSentiment * REACTIONS_CONTRIBUTION);
                logger.debug("Post {} combined sentiment: {} (reactions: {}, comments: {})",
                           instance.getId(), finalSentiment, reactionSentiment, commentSentiment);
            } else if (hasComments) {
                // Only comments available
                finalSentiment = commentSentiment;
            } else {
                // Only reactions available
                finalSentiment = reactionSentiment;
            }

            result.addSentiment(instance.getPostedDate(), finalSentiment);

        } catch (RuntimeException e) {
            // Re-throw runtime exceptions (like process crash)
            throw e;
        }

        return result;
    }

    /**
     * Calculates a sentiment score from Facebook reactions.
     * <p>
     * This method computes a weighted sentiment score based on the distribution
     * of reaction types. Positive reactions (like, love, care, haha) contribute
     * positively, while negative reactions (sad, angry) contribute negatively.
     * </p>
     *
     * @param reactionsCount array of reaction counts indexed by Reaction enum ordinal
     * @return sentiment score normalized to range -1.0 to 1.0, or 0 if no reactions
     */
    private float calculateReactionSentiment(int[] reactionsCount) {
        if (reactionsCount == null || reactionsCount.length == 0) {
            return 0f;
        }

        float weightedSum = 0f;
        int totalReactions = 0;

        // Calculate weighted sum based on reaction types
        if (reactionsCount.length > FacebookPostInstance.Reaction.LIKE.ordinal()) {
            int count = reactionsCount[FacebookPostInstance.Reaction.LIKE.ordinal()];
            weightedSum += count * REACTION_WEIGHT_LIKE;
            totalReactions += count;
        }
        if (reactionsCount.length > FacebookPostInstance.Reaction.LOVE.ordinal()) {
            int count = reactionsCount[FacebookPostInstance.Reaction.LOVE.ordinal()];
            weightedSum += count * REACTION_WEIGHT_LOVE;
            totalReactions += count;
        }
        if (reactionsCount.length > FacebookPostInstance.Reaction.CARE.ordinal()) {
            int count = reactionsCount[FacebookPostInstance.Reaction.CARE.ordinal()];
            weightedSum += count * REACTION_WEIGHT_CARE;
            totalReactions += count;
        }
        if (reactionsCount.length > FacebookPostInstance.Reaction.HAHA.ordinal()) {
            int count = reactionsCount[FacebookPostInstance.Reaction.HAHA.ordinal()];
            weightedSum += count * REACTION_WEIGHT_HAHA;
            totalReactions += count;
        }
        if (reactionsCount.length > FacebookPostInstance.Reaction.WOW.ordinal()) {
            int count = reactionsCount[FacebookPostInstance.Reaction.WOW.ordinal()];
            weightedSum += count * REACTION_WEIGHT_WOW;
            totalReactions += count;
        }
        if (reactionsCount.length > FacebookPostInstance.Reaction.SAD.ordinal()) {
            int count = reactionsCount[FacebookPostInstance.Reaction.SAD.ordinal()];
            weightedSum += count * REACTION_WEIGHT_SAD;
            totalReactions += count;
        }
        if (reactionsCount.length > FacebookPostInstance.Reaction.ANGRY.ordinal()) {
            int count = reactionsCount[FacebookPostInstance.Reaction.ANGRY.ordinal()];
            weightedSum += count * REACTION_WEIGHT_ANGRY;
            totalReactions += count;
        }

        // Return average weighted sentiment (normalized to -1.0 to 1.0 range)
        return totalReactions > 0 ? weightedSum / totalReactions : 0f;
    }
}
