package microwavedram.lboatcam.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry.BoundedDiscrete;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;

@SuppressWarnings({ "unused", "FieldCanBeLocal", "FieldMayBeFinal" })
@Config(name = "lboatcam")
public final class BoatCamConfig implements ConfigData {
    @Comment("Whether the camera should be controlled by this mod.\nNOTE: This setting can be toggled using a key bind.")
    private boolean boatcam = true;
    @Comment("Whether the old boatcam algorithm is used.")
    private boolean oldBoatcam;
    @Comment("0 - Camera moves instantly.\nIncrease - Smoother camera motion. \nValues close to 100 is not supposed to be used")
    @BoundedDiscrete(min = 0, max = 100)
    private int smoothening;
    @Comment("0 - Camera follows the direction boat is facing.\n100 - Camera follow the direction boat is moving.")
    @BoundedDiscrete(min = 0, max = 100)
    private int strength = 50;
    @Comment("Perspective when riding a boat in boat mode. Perspective wont change when this is set to none.")
    private Perspective perspective = Perspective.NONE;
    @Comment("Whether to fix the camera angle at a certain pitch.")
    private boolean fixedPitch;
    @Comment("Fixed vertical angle of the camera when fixedPitch is enabled.")
    @BoundedDiscrete(min = -90, max = 90)
    private int pitch = 10;
    @Comment("Whether to lock horizontal camera movement.")
    private boolean lockedYaw;
    @Comment("Camera angle change of side look keybinds")
    @BoundedDiscrete(min = 0, max = 180)
    private int sideLookAngle = 90;

    private BoatCamConfig() { }

    @Override
    public void validatePostLoad() {
        if (perspective == null) perspective = Perspective.NONE;
    }

    public static BoatCamConfig getConfig() {
        return AutoConfig.getConfigHolder(BoatCamConfig.class).get();
    }

    public static void saveConfig() {
        AutoConfig.getConfigHolder(BoatCamConfig.class).save();
    }

    public float getSmoothening() {
        return (float) smoothening / 100;
    }

    public float getStrength() {
        return (float) strength / 100;
    }

    public boolean isBoatcam() {
        return boatcam;
    }

    public boolean isOldBoatcam() {
        return oldBoatcam;
    }

    public boolean shouldFixPitch() {
        return fixedPitch;
    }

    public boolean shouldLockYaw() {
        return lockedYaw;
    }

    public int getPitch() {
        return pitch;
    }

    public void toggleBoatcam() {
        boatcam = !boatcam;
        saveConfig();
    }

    public Perspective getPerspective() {
        return perspective;
    }

    public int getSideLookAngle() {
        return sideLookAngle;
    }

    public enum Perspective {
        NONE, FIRST_PERSON, THIRD_PERSON
    }
}