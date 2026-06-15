package com.aidsight.presentation.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.Objects;
import java.util.Optional;

/**
 * Utility class for displaying styled JavaFX alert dialogs.
 * <p>
 * This class provides static methods for creating and displaying various types
 * of alert dialogs with consistent styling throughout the application. All dialogs
 * are automatically styled using the application's CSS stylesheet.
 * </p>
 * <p>
 * This class cannot be instantiated as it contains only static utility methods.
 * </p>
 */
public final class DialogUtil {
    private static final String STYLESHEET_PATH = "/com/aidsight/styles/styles.css";

    /**
     * Private constructor to prevent instantiation.
     *
     * @throws AssertionError if instantiation is attempted
     */
    private DialogUtil() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * Displays an error alert dialog.
     * <p>
     * Shows a modal error dialog with the specified title and message.
     * The dialog uses danger styling to indicate an error condition.
     * </p>
     *
     * @param title the title of the alert dialog
     * @param message the error message to display to the user
     */
    public static void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        applyStylesheet(alert);
        styleButton(alert, ButtonType.OK, "alert-button-danger");

        alert.showAndWait();
    }

    /**
     * Displays an information alert dialog.
     * <p>
     * Shows a modal information dialog with the specified title and message.
     * The dialog uses primary styling to indicate informational content.
     * </p>
     *
     * @param title the title of the alert dialog
     * @param message the information message to display to the user
     */
    public static void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        applyStylesheet(alert);
        styleButton(alert, ButtonType.OK, "alert-button-primary");

        alert.showAndWait();
    }

    /**
     * Displays a confirmation alert dialog with Yes/No buttons.
     * <p>
     * Shows a modal confirmation dialog that allows the user to confirm or
     * cancel an action. The dialog provides Yes and No buttons for the user
     * to make a choice.
     * </p>
     *
     * @param title the title of the alert dialog
     * @param headerText the header text of the alert, or null for no header
     * @param message the confirmation message to display to the user
     * @return true if the user clicked Yes, false if the user clicked No or closed the dialog
     */
    public static boolean showConfirmation(String title, String headerText, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(headerText);
        alert.setContentText(message);

        // Customize button text
        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

        applyStylesheet(alert);
        styleButton(alert, ButtonType.YES, "alert-button-danger");
        styleButton(alert, ButtonType.NO, "alert-button-secondary");

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.YES;
    }

    /**
     * Displays a confirmation alert dialog with OK/Cancel buttons.
     * <p>
     * Shows a modal confirmation dialog that allows the user to confirm or
     * cancel an action. The dialog provides OK and Cancel buttons for the user
     * to make a choice.
     * </p>
     *
     * @param title the title of the alert dialog
     * @param headerText the header text of the alert, or null for no header
     * @param message the confirmation message to display to the user
     * @return true if the user clicked OK, false if the user clicked Cancel or closed the dialog
     */
    public static boolean showConfirmationOkCancel(String title, String headerText, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(headerText);
        alert.setContentText(message);

        applyStylesheet(alert);
        styleButton(alert, ButtonType.OK, "alert-button-danger");
        styleButton(alert, ButtonType.CANCEL, "alert-button-secondary");

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * Displays a warning alert dialog.
     * <p>
     * Shows a modal warning dialog with the specified title and message.
     * The dialog uses primary styling to indicate a warning condition.
     * </p>
     *
     * @param title the title of the alert dialog
     * @param message the warning message to display to the user
     */
    public static void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        applyStylesheet(alert);
        styleButton(alert, ButtonType.OK, "alert-button-primary");

        alert.showAndWait();
    }

    /**
     * Applies the application stylesheet to an alert dialog.
     * <p>
     * This method attempts to load and apply the application's CSS stylesheet
     * to the alert dialog. If the stylesheet cannot be found, the dialog is
     * displayed without custom styling.
     * </p>
     *
     * @param alert the alert dialog to apply styling to
     */
    private static void applyStylesheet(Alert alert) {
        try {
            alert.getDialogPane().getStylesheets().add(
                Objects.requireNonNull(
                    DialogUtil.class.getResource(STYLESHEET_PATH)
                ).toExternalForm()
            );
        } catch (NullPointerException e) {
            // Stylesheet not found - continue without styling
            System.err.println("Warning: Could not load stylesheet: " + STYLESHEET_PATH);
        }
    }

    /**
     * Applies a CSS style class to a specific button in an alert dialog.
     * <p>
     * This method locates a button by its type within the alert dialog and
     * applies the specified CSS class to it for custom styling. If the button
     * is not found, the method fails silently.
     * </p>
     *
     * @param alert the alert dialog containing the button
     * @param buttonType the type of button to style (e.g., OK, CANCEL, YES, NO)
     * @param styleClass the CSS class name to apply to the button
     */
    private static void styleButton(Alert alert, ButtonType buttonType, String styleClass) {
        try {
            alert.getDialogPane().lookupButton(buttonType).getStyleClass().add(styleClass);
        } catch (NullPointerException e) {
            // Button not found - continue without styling
        }
    }
}

