package microwavedram.lboatcam;

import microwavedram.lboatcam.config.BoatCamConfig;
import microwavedram.lboatcam.event.LookDirectionChangingEvent;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.DropdownBoxEntry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.util.List;

import static microwavedram.lboatcam.config.BoatCamConfig.getConfig;
import static java.lang.Math.*;
import static net.minecraft.client.util.InputUtil.Type.KEYSYM;
import static net.minecraft.util.Formatting.*;
import static org.lwjgl.glfw.GLFW.*;

public class BoatCamMod implements ModInitializer, LookDirectionChangingEvent {
    // key binds
    private final KeyBinding TOGGLE = new KeyBinding("key.lboatcam.toggle", KEYSYM, -1, "lboatcam");
    private final KeyBinding LOOK_BEHIND = new KeyBinding("key.lboatcam.lookbehind", KEYSYM, GLFW_KEY_B, "lboatcam");
    private final KeyBinding LOOK_LEFT = new KeyBinding("key.lboatcam.lookleft", KEYSYM, -1, "lboatcam");
    private final KeyBinding LOOK_RIGHT = new KeyBinding("key.lboatcam.lookright", KEYSYM, -1, "lboatcam");
    private final KeyBinding RESET_CAMERA = new KeyBinding("key.lboatcam.resetcamera", KEYSYM, -1, "lboatcam");

    // things to remember temporarily
    private Perspective perspective;
    private Perspective previousPerspective;
    private float previousYaw;
    private double offset;
    private double speed;
    private double scale; // px to degrees

    // states
    private boolean lookingBehind;
    private boolean lookingLeft;
    private boolean lookingRight;

    @Override
    public void onInitialize() {
        AutoConfig.register(BoatCamConfig.class, JanksonConfigSerializer::new);
        KeyBindingHelper.registerKeyBinding(TOGGLE);
        KeyBindingHelper.registerKeyBinding(LOOK_BEHIND);
        KeyBindingHelper.registerKeyBinding(LOOK_LEFT);
        KeyBindingHelper.registerKeyBinding(LOOK_RIGHT);
        KeyBindingHelper.registerKeyBinding(RESET_CAMERA);
        ClientTickEvents.START_WORLD_TICK.register(this::onClientEndWorldTick);
        LookDirectionChangingEvent.EVENT.register(this);
        AutoConfig.getGuiRegistry(BoatCamConfig.class).registerPredicateTransformer(
            (guis, s, f, c, d, g) -> dropdownToEnumList(guis, f),
            field -> BoatCamConfig.Perspective.class.isAssignableFrom(field.getType())
        );
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private List<AbstractConfigListEntry> dropdownToEnumList(List<AbstractConfigListEntry> guis, Field field) {
        return guis.stream()
            .filter(DropdownBoxEntry.class::isInstance)
            .map(DropdownBoxEntry.class::cast)
            // transform dropdown menu into enum list
            .map(dropdown -> ConfigEntryBuilder.create()
                .startEnumSelector(dropdown.getFieldName(), BoatCamConfig.Perspective.class, (BoatCamConfig.Perspective) dropdown.getValue())
                .setDefaultValue((BoatCamConfig.Perspective) dropdown.getDefaultValue().orElse(null))
                .setSaveConsumer(p -> {
                    try {
                        field.set(getConfig(), p);
                    } catch (IllegalAccessException ignored) { }
                })
                .setEnumNameProvider(perspective -> switch ((BoatCamConfig.Perspective) perspective) {
                    case FIRST_PERSON -> Text.translatable("text.autoconfig.lboatcam.option.perspective.firstPerson");
                    case THIRD_PERSON -> Text.translatable("text.autoconfig.lboatcam.option.perspective.thirdPerson");
                    case NONE -> Text.translatable("text.autoconfig.lboatcam.option.perspective.none");
                })
                .build())
            .map(AbstractConfigListEntry.class::cast)
            .toList();
    }

    private void onClientEndWorldTick(ClientWorld world) {
        MinecraftClient client = MinecraftClient.getInstance();
        scale = (double) client.getWindow().getWidth() / client.options.getFov().getValue();
        // key bind logic
        if (TOGGLE.wasPressed()) {
            getConfig().toggleBoatcam();
            client.inGameHud.setOverlayMessage(Text.translatable(getConfig().isBoatcam() ? "lboatcam.boatcamEnabled" : "lboatcam.boatcamDisabled")
                .styled(s -> s.withColor(getConfig().isBoatcam() ? GREEN : RED)), false);
        }
        // camera logic
        assert client.player != null;
        if (getConfig().isBoatcam() && client.player.getVehicle() instanceof BoatEntity boat) {
            // first tick riding in boat mode
            if (this.perspective == null) {
                // fix pitch if configured
                if (getConfig().shouldFixPitch()) client.player.setPitch(getConfig().getPitch());
                // save perspective
                this.perspective = client.options.getPerspective();
                this.previousYaw = boat.getYaw();
                // set perspective
                switch (getConfig().getPerspective()) {
                    case FIRST_PERSON -> client.options.setPerspective(Perspective.FIRST_PERSON);
                    case THIRD_PERSON -> client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                }
            }
            calculateYaw(client.player, boat);
            if (RESET_CAMERA.isPressed()) {
                this.offset = 0;
            }
            // if pressed state changed
            if (LOOK_BEHIND.isPressed() != this.lookingBehind) {
                client.player.setPitch(-client.player.getPitch());
                this.lookingBehind = LOOK_BEHIND.isPressed();
                if (this.lookingBehind) {
                    previousPerspective = client.options.getPerspective();
                    client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
                } else {
                    client.options.setPerspective(previousPerspective);
                }
            }
            if (LOOK_LEFT.isPressed() != this.lookingLeft) {
                this.lookingLeft = LOOK_LEFT.isPressed();
                this.offset += this.lookingLeft ? -getConfig().getSideLookAngle() : getConfig().getSideLookAngle();
            }
            if (LOOK_RIGHT.isPressed() != this.lookingRight) {
                this.lookingRight = LOOK_RIGHT.isPressed();
                this.offset += this.lookingRight ? getConfig().getSideLookAngle() : -getConfig().getSideLookAngle();
            }
            client.player.setYaw(client.player.getYaw() + (float) (this.offset));
        } else {
            // first tick after disabling boat mode or leaving boat
            if (this.perspective != null) {
                resetPerspective();
                if (this.lookingBehind) client.player.setPitch(-client.player.getPitch());
                this.lookingBehind = false;
                this.lookingLeft = false;
                this.lookingRight = false;
                this.offset = 0;
            }
        }
    }

    private void resetPerspective() {
        if (this.perspective != null) {
            MinecraftClient.getInstance().options.setPerspective(this.perspective);
            this.perspective = null;
        }
    }

    private void calculateYaw(ClientPlayerEntity player, BoatEntity boat) {
        // yaw calculations
        float yaw = boat.getYaw();
        Vec3d velocity = boat.getVelocity().multiply(1, 0, 1);
        this.speed = velocity.length() * 20;
        if (this.speed > 0.4) {
            float direction = (float) Math.toDegrees(Math.atan2(-velocity.getX(), velocity.getZ()));
            if (getConfig().isOldBoatcam()) {
                yaw += normaliseAngle(direction - yaw) * min(1, (float) this.speed * getConfig().getStrength() / (60 * (1 - getConfig().getStrength())));
            } else {
                float slipAngle = (float) Math.toRadians(normaliseAngle(direction - yaw));
                yaw += Math.toDegrees(slipAngle - atan2(sin(slipAngle) * 16 * 1.6 * (1 - getConfig().getStrength()), (cos(slipAngle) * 16 * 1.6 * (1 - getConfig().getStrength()) + this.speed * getConfig().getStrength())));
            }
            yaw = smoothenAngle(previousYaw, yaw);
            player.setYaw(yaw);
            if (getConfig().shouldFixPitch()) {
                if (this.lookingBehind) {
                    player.setPitch(-getConfig().getPitch());
                } else {
                    player.setPitch(getConfig().getPitch());
                }
            }
        } else {
            player.setYaw(player.getYaw() - (float) this.offset);
        }
        // save pos and yaw
        this.previousYaw = yaw;
    }

    @Override
    public boolean onLookDirectionChanging(double dx, double dy) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null && !(player.getVehicle() instanceof BoatEntity)) return false;
        boolean islockedYaw = getConfig().shouldLockYaw();
        if (getConfig().isBoatcam() && (this.speed >= 0.4)) {
            if (!islockedYaw) this.offset += dx / scale;
            if ((islockedYaw && dx != 0) || (getConfig().shouldFixPitch() && dy != 0)) {
                // prevent horizontal camera movement and cancel camera change by returning true
                // prevent vertical movement as well if configured
                player.changeLookDirection(islockedYaw ? 0 : dx, getConfig().shouldFixPitch() ? 0 : dy);
                return true;
            }
        }
        return false;
    }

    public static float smoothenAngle(float last, float curr) {
        return last + normaliseAngle(curr - last) * (1 - getConfig().getSmoothening());
    }

    public static float normaliseAngle(float angle) {
        return angle - (float) Math.floor(angle / 360 + 0.5) * 360;
    }
}
