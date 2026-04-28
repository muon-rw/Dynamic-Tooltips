package dev.muon.dynamictooltips.api;

import dev.muon.dynamictooltips.handlers.AttributeTooltipHandler;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Public API for cooperating mods to declare attribute display behavior in Dynamic Tooltips.
 *
 * <p>Two kinds of declarations are supported:
 * <ul>
 *   <li><b>Percent attributes</b> — flat (ADD_VALUE) modifiers are rendered as scaled percentages
 *       (e.g. {@code 0.1} as {@code "+10%"}).</li>
 *   <li><b>Base attributes</b> — modifiers are merged with the item's base modifier and rendered
 *       in the green/gold "X when in mainhand" style.</li>
 * </ul>
 *
 * <p>Configuration ({@code attributeColorOverrides} / {@code percentAttributes} /
 * {@code baseAttributes} / {@code baseModifierMappings}) takes precedence over API declarations,
 * letting users override mod-shipped defaults from their own config file.
 */
public final class DynamicTooltipsAPI {

    private static final Map<Identifier, PercentRule> PERCENT_ATTRIBUTES = new ConcurrentHashMap<>();
    private static final Map<Identifier, Identifier> BASE_MODIFIER_MAPPINGS = new ConcurrentHashMap<>();
    private static final Set<Identifier> BASE_ATTRIBUTES = ConcurrentHashMap.newKeySet();

    private static final AtomicInteger VERSION = new AtomicInteger(0);

    private DynamicTooltipsAPI() {}

    /**
     * Declare an attribute whose flat (ADD_VALUE) modifier values should be rendered as percentages.
     *
     * <p>Example: {@code declarePercentAttribute(myAttr, 100)} causes a {@code 0.1} modifier to render
     * as {@code "+10%"}. ADD_MULTIPLIED operations are unaffected — vanilla already renders those as
     * percentages via the translation key.
     *
     * @param attributeId the attribute's registry ID
     * @param scaleFactor multiplier applied to the absolute value before the {@code %} suffix
     */
    public static void declarePercentAttribute(Identifier attributeId, double scaleFactor) {
        Objects.requireNonNull(attributeId, "attributeId");
        PERCENT_ATTRIBUTES.put(attributeId, new PercentRule(scaleFactor, null));
        VERSION.incrementAndGet();
    }

    /**
     * Declare an attribute with a fully custom percent display function.
     *
     * <p>The function is invoked for ADD_VALUE modifiers only; ADD_MULTIPLIED operations continue to
     * use vanilla flat formatting (which already appends {@code %} via the translation key).
     *
     * @param attributeId the attribute's registry ID
     * @param displayFunction transforms the absolute attribute value into a display string
     *                        (typically including the {@code %} suffix)
     */
    public static void declarePercentAttribute(Identifier attributeId, PercentDisplayFunction displayFunction) {
        Objects.requireNonNull(attributeId, "attributeId");
        Objects.requireNonNull(displayFunction, "displayFunction");
        PERCENT_ATTRIBUTES.put(attributeId, new PercentRule(0.0, displayFunction));
        VERSION.incrementAndGet();
    }

    /**
     * Declare an attribute as a base attribute, merging item modifiers with the named base modifier.
     *
     * @param attributeId the attribute's registry ID (e.g. {@code minecraft:attack_damage})
     * @param baseModifierId the modifier ID that represents the base value
     *                       (e.g. {@code minecraft:base_attack_damage})
     */
    public static void declareBaseAttribute(Identifier attributeId, Identifier baseModifierId) {
        Objects.requireNonNull(attributeId, "attributeId");
        Objects.requireNonNull(baseModifierId, "baseModifierId");
        BASE_ATTRIBUTES.add(attributeId);
        BASE_MODIFIER_MAPPINGS.put(attributeId, baseModifierId);
        VERSION.incrementAndGet();
    }

    /**
     * Returns the effective percent-attribute rules: API declarations merged with the user's
     * {@code percentAttributes} config (config wins on conflict). External consumers should use
     * this when deciding how to render an attribute's value.
     */
    public static Map<Identifier, PercentRule> percentAttributes() {
        return AttributeTooltipHandler.effectivePercentRules();
    }

    /** Effective base-attribute set (API declarations merged with config; config wins on conflict). */
    public static Set<Identifier> baseAttributes() {
        return AttributeTooltipHandler.effectiveBaseAttributeIds();
    }

    /** Effective base-modifier mapping (API declarations merged with config; config wins on conflict). */
    public static Map<Identifier, Identifier> baseModifierMappings() {
        return AttributeTooltipHandler.effectiveBaseModifierIds();
    }

    /**
     * Convenience single-id lookup against the effective percent rules.
     * Returns {@code null} when the attribute has no percent rule from either API or config.
     */
    @Nullable
    public static PercentRule percentRuleFor(Identifier attributeId) {
        return AttributeTooltipHandler.getPercentRule(attributeId);
    }

    /**
     * Raw API-declared percent attributes (NO config merge). Internal callers building the merged
     * view need this to avoid recursion through {@link #percentAttributes()}; external callers
     * should generally prefer {@link #percentAttributes()} or {@link #percentRuleFor(Identifier)}.
     */
    @ApiStatus.Internal
    public static Map<Identifier, PercentRule> apiPercentAttributes() {
        return Collections.unmodifiableMap(PERCENT_ATTRIBUTES);
    }

    /** Raw API-declared base-attribute set (NO config merge). See {@link #apiPercentAttributes()}. */
    @ApiStatus.Internal
    public static Set<Identifier> apiBaseAttributes() {
        return Collections.unmodifiableSet(BASE_ATTRIBUTES);
    }

    /** Raw API-declared base-modifier mappings (NO config merge). See {@link #apiPercentAttributes()}. */
    @ApiStatus.Internal
    public static Map<Identifier, Identifier> apiBaseModifierMappings() {
        return Collections.unmodifiableMap(BASE_MODIFIER_MAPPINGS);
    }

    public static int version() {
        return VERSION.get();
    }

    /**
     * Build the green "base value" tooltip line ("12.5 Max Health") for an attribute,
     * formatted to match the un-merged base lines emitted in item attribute tooltips.
     * Pairs with {@link #createModifierComponent} for screens that surface an attribute
     * breakdown outside of item tooltips.
     */
    public static MutableComponent createBaseValueComponent(Attribute attribute, double value) {
        return AttributeTooltipHandler.createBaseValueComponent(attribute, value);
    }

    /**
     * Build a single-modifier tooltip line ("+5 Max Health") with sentiment-aware coloring
     * that respects the {@code attributeColorOverrides} config (FIXED hex / INVERTED logic)
     * and percent-rule scaling. Each call renders exactly one modifier — pass modifiers
     * individually rather than relying on item-tooltip auto-merging.
     */
    public static MutableComponent createModifierComponent(Attribute attribute, AttributeModifier modifier) {
        return AttributeTooltipHandler.createModifierComponent(attribute, modifier);
    }

    /**
     * Internal storage for a percent display rule. {@code displayFunction} takes precedence over
     * {@code scaleFactor} when present.
     */
    public record PercentRule(double scaleFactor, @Nullable PercentDisplayFunction displayFunction) {}

    @FunctionalInterface
    public interface PercentDisplayFunction {
        /**
         * Format an attribute value as a display string.
         *
         * @param absValue the absolute attribute value (always non-negative)
         * @return the display string, typically including the {@code %} suffix
         */
        String format(double absValue);
    }
}
