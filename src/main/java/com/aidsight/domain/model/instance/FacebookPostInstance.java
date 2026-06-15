package com.aidsight.domain.model.instance;

import com.aidsight.domain.model.core.Instance;

import java.util.List;
import java.time.LocalDate;

/**
 * Instance representing a Facebook post with its associated content, comments, and reactions.
 * <p>
 * This class encapsulates all data related to a Facebook post that can be scraped
 * and analyzed. It includes the post's URL, textual content, user comments, publication
 * date, and reaction counts for various Facebook reaction types.
 * </p>
 * <p>
 * Reaction counts are stored in an array indexed by the {@link Reaction} enum ordinal values.
 * </p>
 *
 * @see Instance
 */
public class FacebookPostInstance extends Instance {
    private int id;
    private String url;
    private String content;
    private List<String> comments;
    private LocalDate postedDate;
    private int[] reactionsCount = new int[Reaction.values().length];

    /**
     * Enumeration of Facebook reaction types.
     * <p>
     * These represent the different emotional reactions users can apply to Facebook posts.
     * </p>
     */
    public enum Reaction {
        /** Like reaction */
        LIKE,
        /** Love reaction */
        LOVE,
        /** Care reaction */
        CARE,
        /** Haha (laugh) reaction */
        HAHA,
        /** Wow (surprise) reaction */
        WOW,
        /** Sad reaction */
        SAD,
        /** Angry reaction */
        ANGRY
    }

    /**
     * Constructs an empty FacebookPostInstance.
     * <p>
     * This constructor is primarily used by frameworks and data access objects
     * that populate fields through setters.
     * </p>
     */
    public FacebookPostInstance() {
    }

    /**
     * Constructs a FacebookPostInstance with all fields specified.
     *
     * @param id the unique identifier for this post
     * @param url the URL of the Facebook post
     * @param content the textual content of the post
     * @param comments the list of comment texts on this post
     * @param postedDate the date the post was published
     * @param reactionsCount array of reaction counts indexed by Reaction enum ordinal
     */
    public FacebookPostInstance(int id, String url, String content, List<String> comments, LocalDate postedDate, int[] reactionsCount) {
        this.id = id;
        this.url = url;
        this.content = content;
        this.comments = comments;
        this.postedDate = postedDate;
        this.reactionsCount = reactionsCount;
    }

    /**
     * Gets the post ID.
     *
     * @return the post identifier
     */
    public int getId() {
        return id;
    }

    /**
     * Gets the post URL.
     *
     * @return the URL of the post
     */
    public String getUrl() {
        return url;
    }

    /**
     * Gets the post content.
     *
     * @return the text content
     */
    public String getContent() {
        return content;
    }

    /**
     * Gets the list of comments.
     *
     * @return list of comment strings
     */
    public List<String> getComments() {
        return comments;
    }

    /**
     * Gets the posted date.
     *
     * @return the date the post was published
     */
    public LocalDate getPostedDate() {
        return postedDate;
    }

    /**
     * Gets the reactions count array.
     *
     * @return array of reaction counts indexed by Reaction enum ordinal
     */
    public int[] getReactionsCount() {
        return reactionsCount;
    }

    // Setters

    /**
     * Sets the post ID.
     *
     * @param id the unique identifier for this post
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * Sets the post URL.
     *
     * @param url the URL of the Facebook post
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Sets the post content.
     *
     * @param content the textual content of the post
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Sets the list of comments.
     *
     * @param comments the list of comment texts on this post
     */
    public void setComments(List<String> comments) {
        this.comments = comments;
    }

    /**
     * Sets the posted date.
     *
     * @param postedDate the date the post was published
     */
    public void setPostedDate(LocalDate postedDate) {
        this.postedDate = postedDate;
    }

    /**
     * Sets the reactions count array.
     *
     * @param reactionsCount array of reaction counts indexed by Reaction enum ordinal
     */
    public void setReactionsCount(int[] reactionsCount) {
        this.reactionsCount = reactionsCount;
    }
}
