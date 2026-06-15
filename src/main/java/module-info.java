/**
 * Main module for the OOP application.
 * <p>
 * This module defines the application's dependencies on JavaFX, Selenium, database,
 * logging, and other third-party libraries. It also exports the application's packages
 * to make them accessible to JavaFX and other modules.
 * </p>
 */
module com.app.oop {
    // JavaFX modules
    requires javafx.controls;
    requires javafx.fxml;

    // Java SE modules
    requires java.sql;

    // Logging modules
    requires org.slf4j;
    requires ch.qos.logback.classic; // automatic module

    // Selenium modules
    requires org.seleniumhq.selenium.api;
    requires org.seleniumhq.selenium.chrome_driver;
    requires org.seleniumhq.selenium.support;
    requires io.github.bonigarcia.webdrivermanager; // automatic module

    // HTML parsing
    requires org.jsoup; // automatic module

    // Resilience library
    requires dev.failsafe.core; // automatic module

    // Database
    requires org.xerial.sqlitejdbc; // automatic module


    opens com.aidsight to javafx.fxml;
    exports com.aidsight;
    exports com.aidsight.domain.model.core;
    exports com.aidsight.domain.model.task;
    exports com.aidsight.domain.model.instance;
    exports com.aidsight.domain.processor;
    exports com.aidsight.presentation.config;
    opens com.aidsight.presentation.config to javafx.fxml;
    exports com.aidsight.presentation;
    opens com.aidsight.presentation to javafx.fxml;
    exports com.aidsight.presentation.controller;
    opens com.aidsight.presentation.controller to javafx.fxml;
    exports com.aidsight.infrastructure.scraping.scraper;
    exports com.aidsight.infrastructure.persistence.dao;
    exports com.aidsight.domain.enums;
    exports com.aidsight.domain.model.analysis;
    exports com.aidsight.infrastructure.persistence.schema;
    exports com.aidsight.infrastructure.persistence.dao.impl;
    exports com.aidsight.infrastructure.persistence.connection;
    exports com.aidsight.infrastructure.scraping.scraper.impl;
    exports com.aidsight.infrastructure.scraping.factory;
    opens com.aidsight.infrastructure.scraping.factory to javafx.fxml;
    exports com.aidsight.infrastructure.scraping.manager;
    opens com.aidsight.infrastructure.scraping.manager to javafx.fxml;
    exports com.aidsight.domain.processor.impl;
    exports com.aidsight.infrastructure.config;
    opens com.aidsight.infrastructure.config to javafx.fxml;
    exports com.aidsight.infrastructure.external.python;
    opens com.aidsight.infrastructure.external.python to javafx.fxml;
    exports com.aidsight.domain.service;
    exports com.aidsight.presentation.navigation;
    opens com.aidsight.presentation.navigation to javafx.fxml;
    exports com.aidsight.application.service;
    opens com.aidsight.application.service to javafx.fxml;
    exports com.aidsight.application.controller;
    opens com.aidsight.application.controller to javafx.fxml;
    exports com.aidsight.infrastructure.scraping.exception;
    exports com.aidsight.presentation.util;
    opens com.aidsight.presentation.util to javafx.fxml;
}