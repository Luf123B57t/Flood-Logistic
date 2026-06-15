package com.aidsight.domain.enums;

import com.aidsight.infrastructure.persistence.connection.SQLiteConnectionManager;
import com.aidsight.infrastructure.persistence.dao.impl.FacebookPostDAO;
import com.aidsight.infrastructure.persistence.dao.InstanceDAO;
import com.aidsight.infrastructure.persistence.dao.impl.NewspaperArticleDAO;
import com.aidsight.domain.model.core.Instance;
import com.aidsight.domain.model.instance.FacebookPostInstance;
import com.aidsight.domain.model.instance.NewspaperArticleInstance;
import com.aidsight.infrastructure.scraping.scraper.impl.FacebookPostScraper;
import com.aidsight.infrastructure.scraping.scraper.impl.NewspaperArticleScraper;
import com.aidsight.infrastructure.scraping.scraper.Scraper;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Enumeration of available instance types.
 * <p>
 * This enum provides a centralized registry of all available instance types
 * that can be scraped, stored, and analyzed. Each instance type includes:
 * </p>
 * <ul>
 * <li>Instance class and user-friendly display name</li>
 * <li>Scraper factory for infrastructure acquisition (if applicable)</li>
 * <li>DAO factory for database persistence operations</li>
 * <li>Metadata including unique identifier</li>
 * </ul>
 * <p>
 * To add a new instance type, create a new enum constant with the instance class,
 * scraper factory (null if not scrapable), and DAO factory. The trace-back mechanism
 * from instance class to InstanceType is automatically established during static initialization.
 * </p>
 */
public enum InstanceType {
    FACEBOOK_POST(
        "facebook-post",
        "Facebook Posts",
        "Social media posts from Facebook with comments and reactions",
        FacebookPostInstance.class,
        FacebookPostScraper::new,
        FacebookPostDAO::new
    ),
    NEWSPAPER_ARTICLE(
        "newspaper-article",
        "Newspaper Articles",
        "News articles from online newspaper sources",
        NewspaperArticleInstance.class,
        NewspaperArticleScraper::new,
        NewspaperArticleDAO::new
    );

    private final String id;
    private final String displayName;
    private final String description;
    private final Class<? extends Instance> instanceClass;
    private final Supplier<Scraper<? extends Instance>> scraperFactory;
    private final Function<SQLiteConnectionManager, InstanceDAO<?, ?>> daoFactory;

    // Static reverse lookup map: Instance class -> InstanceType
    private static final Map<Class<? extends Instance>, InstanceType> CLASS_TO_TYPE_MAP = new HashMap<>();

    static {
        // Build reverse lookup map automatically
        for (InstanceType type : values()) {
            CLASS_TO_TYPE_MAP.put(type.instanceClass, type);
        }
    }

    /**
     * Constructor for InstanceType.
     *
     * @param id unique identifier for the instance type
     * @param displayName user-friendly name for GUI display
     * @param description detailed description of the instance type
     * @param instanceClass the instance class type
     * @param scraperFactory factory method to create scraper instances (null if not scrapable)
     * @param daoFactory factory method to create DAO instances
     */
    InstanceType(String id, String displayName, String description,
                Class<? extends Instance> instanceClass,
                Supplier<Scraper<? extends Instance>> scraperFactory,
                Function<SQLiteConnectionManager, InstanceDAO<?, ?>> daoFactory) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.instanceClass = instanceClass;
        this.scraperFactory = scraperFactory;
        this.daoFactory = daoFactory;
    }

    /**
     * Gets the unique identifier for this instance type.
     *
     * @return the instance type ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the display name for GUI presentation.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the description of this instance type.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the instance class type.
     *
     * @return the instance class
     */
    public Class<? extends Instance> getInstanceClass() {
        return instanceClass;
    }

    /**
     * Creates a new instance of the scraper.
     *
     * @return a new scraper instance, or null if this type is not scrapable
     */
    public Scraper<? extends Instance> createScraper() {
        return scraperFactory != null ? scraperFactory.get() : null;
    }

    /**
     * Creates a new instance of the DAO.
     *
     * @param connectionManager the database connection manager
     * @return a new DAO instance
     */
    public InstanceDAO<?, ?> createDAO(SQLiteConnectionManager connectionManager) {
        return daoFactory.apply(connectionManager);
    }

    /**
     * Checks if this instance type is scrapable.
     *
     * @return true if a scraper is available, false otherwise
     */
    public boolean isScrapable() {
        return scraperFactory != null;
    }

    /**
     * Gets all scrapable instance types.
     *
     * @return array of scrapable instance types
     */
    public static InstanceType[] getScrapable() {
        return java.util.Arrays.stream(values())
            .filter(InstanceType::isScrapable)
            .toArray(InstanceType[]::new);
    }

    /**
     * Returns all available instance types.
     *
     * @return array of all instance types
     */
    public static InstanceType[] getAll() {
        return values();
    }

    /**
     * Finds an instance type by its ID.
     *
     * @param id the instance type ID to search for
     * @return the matching InstanceType, or null if not found
     */
    public static InstanceType fromId(String id) {
        for (InstanceType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Finds an instance type by its class (trace-back mechanism).
     * <p>
     * This allows you to get the InstanceType enum from an Instance class,
     * enabling access to all related information (DAO, scraper, etc.)
     * </p>
     *
     * @param instanceClass the instance class
     * @return the InstanceType, or null if not found
     */
    public static InstanceType fromClass(Class<? extends Instance> instanceClass) {
        return CLASS_TO_TYPE_MAP.get(instanceClass);
    }
}

