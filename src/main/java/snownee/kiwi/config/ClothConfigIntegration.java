package snownee.kiwi.config;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import me.shedaniel.clothconfig2.impl.builders.FloatFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.IntFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.IntSliderBuilder;
import me.shedaniel.clothconfig2.impl.builders.LongFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.LongSliderBuilder;
import me.shedaniel.clothconfig2.impl.builders.StringListBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import me.shedaniel.clothconfig2.impl.builders.TextDescriptionBuilder;
import me.shedaniel.clothconfig2.impl.builders.TextFieldBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory;
import net.minecraftforge.fml.ModList;
import snownee.kiwi.config.ConfigHandler.Value;
import snownee.kiwi.config.ConfigUI.Color;
import snownee.kiwi.config.ConfigUI.Hide;
import snownee.kiwi.config.ConfigUI.ItemType;
import snownee.kiwi.config.ConfigUI.Slider;
import snownee.kiwi.config.ConfigUI.TextDescription;
import snownee.kiwi.util.LocalizableItem;
import snownee.kiwi.util.Util;

public class ClothConfigIntegration {

	private static final Component requiresRestart = Component.translatable("kiwi.config.requiresRestart").withStyle(ChatFormatting.RED);

	public static Screen create(Screen parent, String namespace) {
		ConfigBuilder builder = ConfigBuilder.create();
		builder.setParentScreen(parent);
		List<ConfigHandler> configs = KiwiConfigManager.allConfigs.stream().filter($ -> $.getModId().equals(namespace)).toList();
		Joiner joiner = Joiner.on('.');
		for (ConfigHandler config : configs) {
			String titleKey;
			if (config.getFileName().equals(config.getModId() + "-" + config.getType().extension())) {
				titleKey = config.getType().extension();
			} else if (config.getFileName().equals(config.getModId() + "-modules")) {
				titleKey = "modules";
			} else {
				titleKey = config.getFileName();
			}
			Component title;
			if (I18n.exists("kiwi.config." + titleKey)) {
				title = Component.translatable("kiwi.config." + titleKey);
			} else {
				title = Component.literal(Util.friendlyText(titleKey));
			}
			ConfigCategory category = builder.getOrCreateCategory(title);

			Map<String, Consumer<AbstractConfigListEntry<?>>> subCatsMap = Maps.newHashMap();
			List<SubCategoryBuilder> subCats = Lists.newArrayList();
			subCatsMap.put("", category::addEntry);

			for (Value<?> value : config.getValueMap().values()) {
				Hide hide = value.getAnnotation(Hide.class);
				if (hide != null) {
					continue;
				}

				List<String> path = Lists.newArrayList(value.path.split("\\."));
				String key = config.getModId() + ".config." + value.translation;
				if (I18n.exists(key)) {
					title = Component.translatable(key);
				} else {
					title = Component.literal(Util.friendlyText(path.get(path.size() - 1)));
				}

				ConfigEntryBuilder entryBuilder = builder.entryBuilder();
				path.remove(path.size() - 1);
				String subCatKey = joiner.join(path);
				Consumer<AbstractConfigListEntry<?>> subCat = subCatsMap.computeIfAbsent(subCatKey, $ -> {
					String key0 = config.getModId() + ".config." + $;
					Component title0;
					if (I18n.exists(key0)) {
						title0 = Component.translatable(key0);
					} else {
						title0 = Component.literal(Util.friendlyText(path.get(path.size() - 1)));
					}
					SubCategoryBuilder builder0 = entryBuilder.startSubCategory(title0);
					builder0.setExpanded(true);
					subCats.add(builder0);
					return builder0::add;
				});

				TextDescription description = value.field == null ? null : value.field.getAnnotation(TextDescription.class);
				putDescription(subCat, entryBuilder, description, false);

				AbstractConfigListEntry<?> entry = null;
				Class<?> type = value.getType();
				if (type == boolean.class) {
					BooleanToggleBuilder toggle = entryBuilder.startBooleanToggle(title, (Boolean) value.value);
					toggle.setTooltip(createComment(value));
					toggle.setSaveConsumer($ -> value.accept($, config.onChanged));
					toggle.setDefaultValue((Boolean) value.defValue);
					entry = toggle.build();
				} else if (type == int.class) {
					Color color = value.getAnnotation(Color.class);
					if (color != null) {
						ColorFieldBuilder field = entryBuilder.startAlphaColorField(title, (Integer) value.value);
						field.setAlphaMode(color.alpha());
						field.setTooltip(createComment(value));
						field.setSaveConsumer($ -> value.accept($, config.onChanged));
						field.setDefaultValue((Integer) value.defValue);
						entry = field.build();
					} else if (value.getAnnotation(Slider.class) != null) {
						IntSliderBuilder field = entryBuilder.startIntSlider(title, (Integer) value.value, (int) value.min, (int) value.max);
						field.setTooltip(createComment(value));
						field.setSaveConsumer($ -> value.accept($, config.onChanged));
						field.setDefaultValue((Integer) value.defValue);
						entry = field.build();
					} else {
						IntFieldBuilder field = entryBuilder.startIntField(title, (Integer) value.value);
						field.setTooltip(createComment(value));
						if (!Double.isNaN(value.min)) {
							field.setMin((int) value.min);
						}
						if (!Double.isNaN(value.max)) {
							field.setMax((int) value.max);
						}
						field.setSaveConsumer($ -> value.accept($, config.onChanged));
						field.setDefaultValue((Integer) value.defValue);
						entry = field.build();
					}
				} else if (type == double.class) {
					DoubleFieldBuilder field = entryBuilder.startDoubleField(title, (Double) value.value);
					field.setTooltip(createComment(value));
					if (!Double.isNaN(value.min)) {
						field.setMin(value.min);
					}
					if (!Double.isNaN(value.max)) {
						field.setMax(value.max);
					}
					field.setSaveConsumer($ -> value.accept($, config.onChanged));
					field.setDefaultValue((Double) value.defValue);
					entry = field.build();
				} else if (type == float.class) {
					FloatFieldBuilder field = entryBuilder.startFloatField(title, (Float) value.value);
					field.setTooltip(createComment(value));
					if (!Double.isNaN(value.min)) {
						field.setMin((float) value.min);
					}
					if (!Double.isNaN(value.max)) {
						field.setMax((float) value.max);
					}
					field.setSaveConsumer($ -> value.accept($, config.onChanged));
					field.setDefaultValue((Float) value.defValue);
					entry = field.build();
				} else if (type == long.class) {
					if (value.getAnnotation(Slider.class) != null) {
						LongSliderBuilder field = entryBuilder.startLongSlider(title, (Long) value.value, (long) value.min, (long) value.max);
						field.setTooltip(createComment(value));
						field.setSaveConsumer($ -> value.accept($, config.onChanged));
						field.setDefaultValue((Long) value.defValue);
						entry = field.build();
					} else {
						LongFieldBuilder field = entryBuilder.startLongField(title, (Long) value.value);
						field.setTooltip(createComment(value));
						if (!Double.isNaN(value.min)) {
							field.setMin((long) value.min);
						}
						if (!Double.isNaN(value.max)) {
							field.setMax((long) value.max);
						}
						field.setSaveConsumer($ -> value.accept($, config.onChanged));
						field.setDefaultValue((Long) value.defValue);
						entry = field.build();
					}
				} else if (type == String.class) {
					TextFieldBuilder field = entryBuilder.startTextField(title, (String) value.value);
					field.setTooltip(createComment(value));
					field.setSaveConsumer($ -> value.accept($, config.onChanged));
					field.setDefaultValue((String) value.defValue);
					entry = field.build();
				} else if (Enum.class.isAssignableFrom(type)) {
					EnumSelectorBuilder<Enum<?>> field = entryBuilder.startEnumSelector(title, (Class<Enum<?>>) type, (Enum<?>) value.value);
					field.setSaveConsumer($ -> value.accept($, config.onChanged));
					field.setDefaultValue((Enum<?>) value.defValue);
					field.setEnumNameProvider($ -> {
						if ($ instanceof LocalizableItem item) {
							return item.getDisplayName().copy();
						} else {
							return Component.literal($.name());
						}
					});
					field.setTooltipSupplier($ -> {
						List<Component> tooltip = Lists.newArrayList();
						if ($ instanceof LocalizableItem item) {
							tooltip.add(item.getDisplayName().copy().append(" - ").append(item.getDescription()));
						}
						createComment(value).map(Arrays::asList).ifPresent(tooltip::addAll);
						return tooltip.isEmpty() ? Optional.empty() : Optional.of(tooltip.toArray(Component[]::new));
					});
					entry = field.build();
				} else if (List.class.isAssignableFrom(type)) {
					ItemType itemType = value.field.getAnnotation(ItemType.class);
					if (itemType == null)
						continue;
					if (itemType.value() == String.class) {
						StringListBuilder field = entryBuilder.startStrList(title, (List<String>) value.value);
						field.setTooltip(createComment(value));
						field.setSaveConsumer($ -> value.accept($, config.onChanged));
						field.setDefaultValue((List<String>) value.defValue);
						entry = field.build();
					}
				}
				if (entry != null) {
					entry.setRequiresRestart(value.requiresRestart);
					subCat.accept(entry);
				}

				putDescription(subCat, entryBuilder, description, true);
			}
			subCats.forEach($ -> category.addEntry($.build()));
		}
		builder.setSavingRunnable(() -> {
			configs.forEach(ConfigHandler::save);
		});
		return builder.build();
	}

	private static void putDescription(Consumer<AbstractConfigListEntry<?>> subCat, ConfigEntryBuilder entryBuilder, TextDescription description, boolean after) {
		if (description == null || description.after() != after) {
			return;
		}
		Component component;
		if (I18n.exists(description.value())) {
			component = Component.translatable(description.value());
		} else {
			component = Component.literal(description.value());
		}
		TextDescriptionBuilder builder = entryBuilder.startTextDescription(component);
		subCat.accept(builder.build());
	}

	private static Optional<Component[]> createComment(Value<?> value) {
		List<Component> tooltip = Lists.newArrayList();
		if (value.comment != null) {
			for (String comment : value.comment) {
				tooltip.add(Component.literal(comment));
			}
		}
		if (value.requiresRestart) {
			tooltip.add(requiresRestart);
		}
		return tooltip.isEmpty() ? Optional.empty() : Optional.of(tooltip.toArray(Component[]::new));
	}

	static void init() {
		List<String> mods = KiwiConfigManager.allConfigs.stream().map(ConfigHandler::getModId).distinct().toList();
		for (String mod : mods) {
			ModList.get().getModContainerById(mod).ifPresent($ -> $.registerExtensionPoint(ConfigScreenFactory.class, () -> new ConfigScreenFactory((minecraft, screen) -> ClothConfigIntegration.create(screen, mod))));
		}
	}

}