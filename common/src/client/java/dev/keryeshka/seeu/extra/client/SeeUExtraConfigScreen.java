package dev.keryeshka.seeu.extra.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Client-side opt-in list of every locally registered entity type.
 * The server's selector and global limits remain authoritative.
 */
public final class SeeUExtraConfigScreen extends Screen {
    private final Screen parent;
    private final SeeUExtraClientConfig config;
    private final List<EntityRow> allTypes = new ArrayList<>();
    private String filter = "";
    private int page;
    private int visibleRows;
    private int listLeft;
    private int listWidth;
    private EditBox searchBox;

    public SeeUExtraConfigScreen(Screen parent, SeeUExtraClientConfig config) {
        super(Component.literal("SeeU Extra — Distant Entities"));
        this.parent = parent;
        this.config = config;
        for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
            if (key == null || key.toString().equals("minecraft:player")) {
                continue;
            }
            allTypes.add(new EntityRow(key.toString(), entityType.getDescription().getString()));
        }
        allTypes.sort(Comparator.comparing(EntityRow::id));
    }

    @Override
    protected void init() {
        listWidth = Math.min(730, width - 24);
        listLeft = (width - listWidth) / 2;
        visibleRows = Math.max(2, Math.min(8, (height - 148) / 25));
        int top = 63;

        EditBox search = new EditBox(font, listLeft + 4, 36, listWidth - 8, 20,
                Component.literal("Search entity name or ID"));
        search.setMaxLength(100);
        search.setHint(Component.literal("Search registered entities..."));
        search.setValue(filter);
        search.setResponder(value -> {
            if (!value.equals(filter)) {
                filter = value;
                page = 0;
                refresh();
            }
        });
        searchBox = addRenderableWidget(search);

        List<EntityRow> filtered = filteredTypes();
        int pages = Math.max(1, (filtered.size() + visibleRows - 1) / visibleRows);
        page = Math.max(0, Math.min(page, pages - 1));
        int begin = page * visibleRows;
        int end = Math.min(begin + visibleRows, filtered.size());

        for (int i = begin; i < end; i++) {
            EntityRow row = filtered.get(i);
            int y = top + (i - begin) * 25;
            boolean active = config.isEntityEnabled(row.id());
            addRenderableWidget(Button.builder(Component.literal(active ? "ON" : "OFF"),
                    button -> {
                        config.setEntityEnabled(row.id(), !config.isEntityEnabled(row.id()));
                        refresh();
                    }).bounds(listLeft + listWidth - 236, y, 53, 20).build());
            // This setting keeps the GeckoLib proxy controller alive across
            // native-chunk handoffs. Mods with a custom native renderer can
            // register an adapter to suppress duplicate native draws.
            boolean anim = config.hasPersistentAnimation(row.id());
            addRenderableWidget(Button.builder(Component.literal(anim ? "Anim ON" : "Anim OFF"),
                    button -> {
                        config.setPersistentAnimation(row.id(), !anim);
                        refresh();
                    }).bounds(listLeft + listWidth - 179, y, 73, 20).build()).active = active;
            EditBox distance = new EditBox(font, listLeft + listWidth - 102, y, 98, 20,
                    Component.literal("Distance in blocks for " + row.id()));
            distance.setMaxLength(5);
            distance.setValue(Integer.toString(active ? config.entityDistance(row.id()) : config.defaultEntityDistance()));
            distance.setFilter(value -> value.matches("[0-9]{0,5}"));
            distance.setEditable(active);
            distance.setResponder(value -> {
                if (value.isEmpty()) {
                    return;
                }
                try {
                    config.setEntityDistance(row.id(), Integer.parseInt(value));
                } catch (NumberFormatException ignored) {
                    // A partially entered number is not saved until valid.
                }
            });
            addRenderableWidget(distance);
        }

        int footer = height - 65;
        addRenderableWidget(Button.builder(Component.literal("< Prev"), button -> {
            page--;
            refresh();
        }).bounds(listLeft, footer, 74, 20).build()).active = page > 0;

        addRenderableWidget(Button.builder(Component.literal("Next >"), button -> {
            page++;
            refresh();
        }).bounds(listLeft + listWidth - 74, footer, 74, 20).build()).active = page + 1 < pages;

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 - 54, height - 34, 108, 20).build());
    }

    private void refresh() {
        boolean searchFocused = searchBox != null && searchBox.isFocused();
        clearWidgets();
        init();
        if (searchFocused) {
            setFocused(searchBox);
            searchBox.setFocused(true);
        }
    }

    private List<EntityRow> filteredTypes() {
        String needle = filter.toLowerCase(Locale.ROOT).trim();
        if (needle.isEmpty()) {
            return allTypes;
        }
        return allTypes.stream().filter(row -> row.id().contains(needle)
                || row.name().toLowerCase(Locale.ROOT).contains(needle)).toList();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 13, 0xFFFFFF);
        graphics.drawString(font, "Opt in by entity. Anim ON preserves GeckoLib controllers; Voxy depth is automatic.", listLeft + 4, 26, 0xAFAFAF, false);
        List<EntityRow> filtered = filteredTypes();
        int start = page * visibleRows;
        int end = Math.min(start + visibleRows, filtered.size());
        for (int i = start; i < end; i++) {
            EntityRow row = filtered.get(i);
            String label = row.name().equalsIgnoreCase(row.id()) ? row.id()
                    : row.name() + " (" + row.id() + ")";
            int maxTextWidth = listWidth - 245;
            if (font.width(label) > maxTextWidth) {
                label = font.plainSubstrByWidth(label, Math.max(20, maxTextWidth - font.width("..."))) + "...";
            }
            graphics.drawString(font, label, listLeft + 4,
                    69 + (i - start) * 25, 0xFFFFFF, false);
        }
        int pages = Math.max(1, (filtered.size() + visibleRows - 1) / visibleRows);
        graphics.drawCenteredString(font, "Page " + (page + 1) + "/" + pages
                + "  ·  Enabled " + config.entityDistances.size() + "/"
                + allTypes.size(), width / 2, height - 59, 0xD0D0D0);
        graphics.drawCenteredString(font,
                "Server restrictions still apply. Changes sync automatically on the next frame.",
                width / 2, height - 45, 0xAAAAAA);
    }

    @Override
    public void onClose() {
        config.save();
        Minecraft.getInstance().setScreen(parent);
    }

    private record EntityRow(String id, String name) {}
}
