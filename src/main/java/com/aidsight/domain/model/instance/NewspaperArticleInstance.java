package com.aidsight.domain.model.instance;

import com.aidsight.domain.model.core.Instance;

import java.time.LocalDate;

/**
 * Instance representing a newspaper article with its content and metadata.
 * <p>
 * This class encapsulates all data related to a newspaper article that can be
 * scraped and analyzed. It includes the article's URL, author information,
 * textual content, and publication date.
 * </p>
 *
 * @see Instance
 */
public class NewspaperArticleInstance extends Instance {
    private int id;
    private String url;
    private String author;
    private String content;
    private LocalDate postedDate;

    /**
     * Retrieves the unique identifier for this article.
     *
     * @return the article's database ID
     */
    public int getId() {
        return id;
    }

    /**
     * Retrieves the URL of the article.
     *
     * @return the article's web address
     */
    public String getUrl() {
        return url;
    }

    /**
     * Retrieves the author of the article.
     *
     * @return the author's name or byline
     */
    public String getAuthor() {
        return author;
    }

    /**
     * Retrieves the textual content of the article.
     *
     * @return the article's full text content
     */
    public String getContent() {
        return content;
    }

    /**
     * Retrieves the publication date of the article.
     *
     * @return the date the article was published
     */
    public LocalDate getPostedDate() {
        return postedDate;
    }

    // Setters

    /**
     * Sets the unique identifier for this article.
     *
     * @param id the article's database ID
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * Sets the URL of the article.
     *
     * @param url the article's web address
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Sets the author of the article.
     *
     * @param author the author's name or byline
     */
    public void setAuthor(String author) {
        this.author = author;
    }

    /**
     * Sets the textual content of the article.
     *
     * @param content the article's full text content
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Sets the publication date of the article.
     *
     * @param postedDate the date the article was published
     */
    public void setPostedDate(LocalDate postedDate) {
        this.postedDate = postedDate;
    }
}
