package com.aidsight.domain.model.core;

import com.aidsight.domain.enums.InstanceType;

/**
 * Base abstract class for all infrastructure instances.
 * <p>
 * Instances represent the infrastructure to be analyzed by tasks. Each instance must
 * provide a descriptive name and description that identifies its type.
 * </p>
 * <p>
 * The {@link #getName()} and {@link #getDescription()} methods automatically
 * retrieve values from the corresponding {@link InstanceType} enum.
 * </p>
 */
public abstract class Instance {
    /**
     * Returns the name of this instance type.
     * <p>
     * Retrieves the display name from the corresponding {@link InstanceType} enum.
     * Returns "No Name" if the instance type is not found.
     * </p>
     *
     * @return a descriptive name for this instance type
     */
    public String getName() {
        InstanceType type = InstanceType.fromClass(this.getClass());
        return type != null ? type.getDisplayName() : "No Name";
    }

    /**
     * Returns a description of this instance type.
     * <p>
     * Retrieves the description from the corresponding {@link InstanceType} enum.
     * Returns "No Description" if the instance type is not found.
     * </p>
     *
     * @return a description for this instance type
     */
    public String getDescription() {
        InstanceType type = InstanceType.fromClass(this.getClass());
        return type != null ? type.getDescription() : "No Description";
    }
}
