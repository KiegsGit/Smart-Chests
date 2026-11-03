package com.smartchests.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.smartchests.DataPayload;
import com.smartchests.RequestPayload;
import com.smartchests.TerminalMenu;
import com.smartchests.TerminalService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * SMART STORAGE TERMINAL. Everything is drawn here with plain rectangles and text so the look is fully under our
 * control (dark navy, cyan highlights, bevelled slots). All real work is done by the server; this screen only shows
 * the data it is sent and sends requests.
 */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {
	private static final int W = 288;
	private static final int H = 196;
	private static final int COLS = 9;
	private static final int ROWS = 6;
	private static final int CELL = 18;

	// palette
	private static final int C_DIM = 0xC0050818;
	private static final int C_PANEL = 0xFF0A1030;
	private static final int C_BORDER = 0xFF2B6CFF;
	private static final int C_BAR = 0xFF0E1A4A;
	private static final int C_CYAN = 0xFF3FE0FF;
	private static final int C_CYAN_D = 0xFF1EA7D9;
	private static final int C_SLOT_D = 0xFF02051A;
	private static final int C_SLOT = 0xFF0B1440;
	private static final int C_SLOT_L = 0xFF3A5CB8;
	private static final int C_TEXT = 0xFFDDF6FF;
	private static final int C_DIMTEXT = 0xFF8FA6D8;
	private static final int C_WARN = 0xFFFFC857;

	private int px;
	private int py;
	private EditBox search;
	private Button refreshBtn;
	private Button filterBtn;
	private Button sortBtn;
	private Button takeStackBtn;
	private Button takeAllBtn;
	private Button depMatchBtn;
	private Button depAllBtn;
	private final List<AbstractWidget> widgets = new ArrayList<>();

	private DataPayload data;
	private int seenVersion = -1;
	private List<DataPayload.Entry> view = List.of();
	private ItemStack selected = ItemStack.EMPTY;
	private int scroll;
	private int detailScroll;
	private int filter;
	private int sort;
	private long confirmUntil;
	private boolean waiting = true;
	private Component status = Component.translatable("gui.smartchests.scanning");

	public TerminalScreen(TerminalMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = W;
		this.imageHeight = H;
		ClientTerminalState.reset();
	}

	@Override
	protected void init() {
		super.init();
		this.px = (this.width - W) / 2;
		this.py = (this.height - H) / 2;
		this.widgets.clear();

		String old = this.search == null ? "" : this.search.getValue();
		this.search = new EditBox(this.font, px + 82, py + 5, 84, 14, Component.translatable("gui.smartchests.search"));
		this.search.setHint(Component.translatable("gui.smartchests.search_hint"));
		this.search.setMaxLength(48);
		this.search.setValue(old);
		this.search.setResponder(s -> {
			scroll = 0;
			rebuildView();
		});
		add(this.search);

		refreshBtn = add(Button.builder(Component.translatable("gui.smartchests.refresh"), b -> send(TerminalService.ACT_REFRESH, false))
			.bounds(px + 170, py + 4, 40, 16).build());
		filterBtn = add(Button.builder(Component.empty(), b -> {
			filter = (filter + 1) % ItemCategory.values().length;
			scroll = 0;
			rebuildView();
		}).bounds(px + 8, py + 26, 110, 16).build());
		sortBtn = add(Button.builder(Component.empty(), b -> {
			sort = (sort + 1) % 4;
			rebuildView();
		}).bounds(px + 122, py + 26, 110, 16).build());

		int by = py + 160;
		takeStackBtn = add(Button.builder(Component.translatable("gui.smartchests.take_stack"), b -> send(TerminalService.ACT_TAKE_STACK, false))
			.bounds(px + 8, by, 66, 16).build());
		takeAllBtn = add(Button.builder(Component.translatable("gui.smartchests.take_all"), b -> send(TerminalService.ACT_TAKE_ALL, false))
			.bounds(px + 76, by, 66, 16).build());
		depMatchBtn = add(Button.builder(Component.translatable("gui.smartchests.deposit_match"), b -> send(TerminalService.ACT_DEPOSIT_MATCH, false))
			.bounds(px + 144, by, 66, 16).build());
		depAllBtn = add(Button.builder(Component.translatable("gui.smartchests.deposit_all"), b -> {
			long now = System.currentTimeMillis();
			if (now < confirmUntil) {
				confirmUntil = 0;
				send(TerminalService.ACT_DEPOSIT_ALL, true);
			} else {
				confirmUntil = now + 4000;
				status = Component.translatable("gui.smartchests.confirm_deposit");
			}
		}).bounds(px + 212, by, 66, 16).build());

		this.search.setFocused(true);
		this.setFocused(this.search);
		updateLabels();
	}

	private <T extends AbstractWidget> T add(T widget) {
		this.widgets.add(widget);
		this.addWidget(widget); // children list: focus + input. Drawing is done manually, behind/after our own panel.
		return widget;
	}

	// ------------------------------------------------------------------ data

	private void send(int action, boolean confirm) {
		ItemStack rep = selected.isEmpty() ? ItemStack.EMPTY : selected.copyWithCount(1);
		ClientPlayNetworking.send(new RequestPayload(action, rep, confirm));
		waiting = true;
	}

	private void pollState() {
		if (ClientTerminalState.version == seenVersion || ClientTerminalState.latest == null) {
			return;
		}
		seenVersion = ClientTerminalState.version;
		data = ClientTerminalState.latest;
		waiting = false;
		switch (data.msg()) {
			case TerminalService.MSG_TOOK -> status = Component.translatable("gui.smartchests.msg.took", data.a(), data.b());
			case TerminalService.MSG_DEPOSITED -> status = Component.translatable("gui.smartchests.msg.deposited", data.a(), data.b());
			case TerminalService.MSG_REFRESHED -> status = Component.translatable("gui.smartchests.msg.refreshed");
			case TerminalService.MSG_ERROR -> status = Component.translatable("gui.smartchests.msg.error." + data.a());
			default -> status = Component.translatable("gui.smartchests.msg.ready", data.radius());
		}
		if (!selected.isEmpty()) {
			boolean still = false;
			for (DataPayload.Entry e : data.entries()) {
				if (ItemStack.isSameItemSameComponents(e.stack(), selected)) {
					still = true;
				}
			}
			if (!still) {
				selected = ItemStack.EMPTY;
			}
		}
		rebuildView();
	}

	private void rebuildView() {
		if (data == null) {
			view = List.of();
			updateLabels();
			return;
		}
		String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
		ItemCategory cat = ItemCategory.values()[filter];
		List<DataPayload.Entry> out = new ArrayList<>();
		for (DataPayload.Entry e : data.entries()) {
			if (cat != ItemCategory.ALL && ItemCategory.classify(e.stack()) != cat) {
				continue;
			}
			if (!q.isEmpty() && !name(e).toLowerCase(Locale.ROOT).contains(q)) {
				continue;
			}
			out.add(e);
		}
		Comparator<DataPayload.Entry> byName = Comparator.comparing(e -> name(e).toLowerCase(Locale.ROOT));
		switch (sort) {
			case 0 -> out.sort(Comparator.comparingInt(DataPayload.Entry::total).reversed().thenComparing(byName));
			case 1 -> out.sort(Comparator.comparingInt(DataPayload.Entry::total).thenComparing(byName));
			case 2 -> out.sort(byName);
			default -> out.sort(byName.reversed());
		}
		view = out;
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
		updateLabels();
	}

	private static String name(DataPayload.Entry e) {
		return e.stack().getHoverName().getString();
	}

	private int maxScroll() {
		int rows = (view.size() + COLS - 1) / COLS;
		return Math.max(0, rows - ROWS);
	}

	private DataPayload.Entry selectedEntry() {
		if (data == null || selected.isEmpty()) {
			return null;
		}
		for (DataPayload.Entry e : data.entries()) {
			if (ItemStack.isSameItemSameComponents(e.stack(), selected)) {
				return e;
			}
		}
		return null;
	}

	private void updateLabels() {
		if (filterBtn == null) {
			return;
		}
		filterBtn.setMessage(Component.translatable("gui.smartchests.filter", Component.translatable("gui.smartchests.filter." + ItemCategory.values()[filter].key)));
		sortBtn.setMessage(Component.translatable("gui.smartchests.sort", Component.translatable("gui.smartchests.sort." + sort)));
		boolean has = !selected.isEmpty();
		takeStackBtn.active = has;
		takeAllBtn.active = has;
		depMatchBtn.active = has;
		depAllBtn.setMessage(System.currentTimeMillis() < confirmUntil
			? Component.translatable("gui.smartchests.confirm")
			: Component.translatable("gui.smartchests.deposit_all"));
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (event.button() == 0 && inGrid(mx, my)) {
			int col = (int) ((mx - (px + 8)) / CELL);
			int row = (int) ((my - (py + 48)) / CELL);
			int idx = (scroll + row) * COLS + col;
			if (col >= 0 && col < COLS && idx >= 0 && idx < view.size()) {
				selected = view.get(idx).stack().copyWithCount(1);
				detailScroll = 0;
				updateLabels();
			}
			return true;
		}
		boolean handled = false;
		for (AbstractWidget w : widgets) {
			if (w.mouseClicked(event, doubleClick)) {
				handled = true;
				if (w == search) {
					this.setFocused(search);
				}
				break;
			}
		}
		if (!handled) {
			search.setFocused(false);
		}
		updateLabels();
		return handled;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		if (inGrid(mx, my)) {
			scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
			return true;
		}
		if (mx >= px + 180 && mx < px + W - 8 && my >= py + 48 && my < py + 156) {
			DataPayload.Entry e = selectedEntry();
			int max = e == null ? 0 : Math.max(0, e.locs().size() - 4);
			detailScroll = Math.max(0, Math.min(max, detailScroll - (int) Math.signum(scrollY)));
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_ESCAPE) {
			this.onClose();
			return true;
		}
		if (search != null && search.isFocused()) {
			return search.keyPressed(event) || search.canConsumeInput();
		}
		return super.keyPressed(event);
	}

	private boolean inGrid(double mx, double my) {
		return mx >= px + 8 && mx < px + 8 + COLS * CELL && my >= py + 48 && my < py + 48 + ROWS * CELL;
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		pollState();
		updateLabels();
		g.fill(0, 0, this.width, this.height, C_DIM);

		// window
		frame(g, px, py, px + W, py + H, C_PANEL, C_BORDER);
		g.fill(px + 1, py + 1, px + W - 1, py + 22, C_BAR);
		g.text(this.font, Component.translatable("gui.smartchests.title"), px + 8, py + 7, C_CYAN, true);

		// container count + animated scan bar
		String count = data == null ? "..." : Component.translatable("gui.smartchests.containers", data.containers()).getString();
		g.text(this.font, count, px + W - 8 - this.font.width(count), py + 7, C_TEXT, true);
		scanBar(g);
		if (data != null && data.unloadedChunks() > 0) {
			g.text(this.font, "!", px + W - 20, py + 30, C_WARN, true);
			if (mx >= px + W - 24 && mx < px + W - 12 && my >= py + 26 && my < py + 42) {
				g.setTooltipForNextFrame(this.font, Component.translatable("gui.smartchests.unloaded", data.unloadedChunks()), mx, my);
			}
		}

		// grid
		int gx = px + 8;
		int gy = py + 48;
		DataPayload.Entry hover = null;
		int hx = 0;
		int hy = 0;
		for (int r = 0; r < ROWS; r++) {
			for (int c = 0; c < COLS; c++) {
				int cx = gx + c * CELL;
				int cy = gy + r * CELL;
				slot(g, cx, cy);
				int idx = (scroll + r) * COLS + c;
				if (idx >= view.size()) {
					continue;
				}
				DataPayload.Entry e = view.get(idx);
				boolean sel = !selected.isEmpty() && ItemStack.isSameItemSameComponents(e.stack(), selected);
				if (sel) {
					border(g, cx, cy, cx + CELL, cy + CELL, C_CYAN);
				}
				g.item(e.stack(), cx + 1, cy + 1);
				String n = shortCount(e.total());
				g.text(this.font, n, cx + 17 - this.font.width(n), cy + 9, 0xFFFFFFFF, true);
				if (mx >= cx && mx < cx + CELL && my >= cy && my < cy + CELL) {
					hover = e;
					hx = mx;
					hy = my;
				}
			}
		}
		if (view.isEmpty()) {
			Component empty = Component.translatable(data == null ? "gui.smartchests.scanning" : "gui.smartchests.empty");
			g.text(this.font, empty, gx + (COLS * CELL - this.font.width(empty)) / 2, gy + 50, C_DIMTEXT, true);
		}
		// scrollbar
		int sx = gx + COLS * CELL + 3;
		g.fill(sx, gy, sx + 3, gy + ROWS * CELL, C_SLOT_D);
		if (maxScroll() > 0) {
			int total = (view.size() + COLS - 1) / COLS;
			int barH = Math.max(8, ROWS * CELL * ROWS / total);
			int barY = gy + (ROWS * CELL - barH) * scroll / maxScroll();
			g.fill(sx, barY, sx + 3, barY + barH, C_CYAN_D);
		}

		details(g);

		// status line
		g.text(this.font, status, px + 8, py + 181, waiting ? C_WARN : C_DIMTEXT, false);

		for (AbstractWidget w : widgets) {
			w.extractRenderState(g, mx, my, delta);
		}
		if (hover != null) {
			g.setTooltipForNextFrame(this.font, hover.stack().getHoverName(), hx, hy);
		}
	}

	private void details(GuiGraphicsExtractor g) {
		int dx = px + 180;
		int dy = py + 48;
		int dw = W - 8 - 180;
		frame(g, dx - 2, dy - 2, dx + dw + 2, dy + 108, C_SLOT, C_SLOT_L);
		DataPayload.Entry e = selectedEntry();
		if (e == null) {
			Component hint = Component.translatable("gui.smartchests.select_hint");
			g.text(this.font, hint, dx + 2, dy + 4, C_DIMTEXT, false);
			return;
		}
		g.item(e.stack(), dx, dy);
		g.text(this.font, fit(name(e).toUpperCase(Locale.ROOT), dw - 20), dx + 20, dy + 4, C_CYAN, true);
		g.text(this.font, Component.translatable("gui.smartchests.total", e.total(), e.locs().size()), dx + 2, dy + 20, C_TEXT, false);
		int y = dy + 33;
		for (int i = detailScroll; i < e.locs().size() && y + 18 <= dy + 106; i++) {
			DataPayload.Loc l = e.locs().get(i);
			g.text(this.font, fit(Component.translatable("gui.smartchests.loc", kindName(l.kind()), l.index(), l.count()).getString(), dw), dx + 2, y, C_TEXT, false);
			g.text(this.font, l.pos().getX() + ", " + l.pos().getY() + ", " + l.pos().getZ(), dx + 2, y + 9, C_DIMTEXT, false);
			y += 19;
		}
		if (e.locs().size() > 4) {
			g.text(this.font, "\u2195", dx + dw - 6, dy + 20, C_DIMTEXT, false);
		}
	}

	private static String kindName(int kind) {
		return Component.translatable("gui.smartchests.kind." + kind).getString();
	}

	private String fit(String s, int width) {
		if (this.font.width(s) <= width) {
			return s;
		}
		String t = s;
		while (t.length() > 1 && this.font.width(t + "..") > width) {
			t = t.substring(0, t.length() - 1);
		}
		return t + "..";
	}

	/** Thin line under the title bar; a bright segment sweeps along it, faster and brighter while waiting for the server. */
	private void scanBar(GuiGraphicsExtractor g) {
		int y = py + 22;
		g.fill(px + 1, y, px + W - 1, y + 1, C_CYAN_D);
		long t = System.currentTimeMillis();
		int span = W + 40;
		int speed = waiting ? 3 : 14;
		int pos = (int) ((t / speed) % span) - 40;
		int x0 = Math.max(px + 1, px + pos);
		int x1 = Math.min(px + W - 1, px + pos + 40);
		if (x1 > x0) {
			g.fill(x0, y, x1, y + 1, waiting ? 0xFFFFFFFF : C_CYAN);
		}
	}

	private void slot(GuiGraphicsExtractor g, int x, int y) {
		g.fill(x, y, x + CELL, y + CELL, C_SLOT_L);
		g.fill(x, y, x + CELL - 1, y + CELL - 1, C_SLOT_D);
		g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, C_SLOT);
	}

	private static void frame(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int fill, int border) {
		g.fill(x0, y0, x1, y1, border);
		g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
	}

	private static void border(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
		g.fill(x0, y0, x1, y0 + 1, color);
		g.fill(x0, y1 - 1, x1, y1, color);
		g.fill(x0, y0, x0 + 1, y1, color);
		g.fill(x1 - 1, y0, x1, y1, color);
	}

	private static String shortCount(int n) {
		if (n < 1000) {
			return Integer.toString(n);
		}
		if (n < 10000) {
			return (n / 100) / 10.0 + "k";
		}
		if (n < 1_000_000) {
			return (n / 1000) + "k";
		}
		return (n / 1_000_000) + "M";
	}
}
