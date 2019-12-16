package de.markusfisch.android.pielauncher.widget;

import de.markusfisch.android.pielauncher.content.AppMenu;
import de.markusfisch.android.pielauncher.graphics.Converter;
import de.markusfisch.android.pielauncher.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;

public class AppPieView extends SurfaceView {
	public interface OpenListListener {
		void onOpenList();
	}

	public static final AppMenu appMenu = new AppMenu();

	private final ArrayList<AppMenu.Icon> backup = new ArrayList<>();
	private final ArrayList<AppMenu.Icon> ungrabbedIcons = new ArrayList<>();
	private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
	private final Paint selectedPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
	private final Point touch = new Point();
	private final Point lastTouch = new Point();
	private final SurfaceHolder surfaceHolder;
	private final Rect iconAddRect = new Rect();
	private final Bitmap iconAdd;
	private final Rect iconRemoveRect = new Rect();
	private final Bitmap iconRemove;
	private final Rect iconInfoRect = new Rect();
	private final Bitmap iconInfo;
	private final Rect iconDoneRect = new Rect();
	private final Bitmap iconDone;
	private final int transparentBackgroundColor;
	private final float dp;

	private int viewWidth;
	private int viewHeight;
	private int radius;
	private int tapTimeout;
	private float touchSlopSq;
	private OpenListListener listListener;
	private AppMenu.Icon grabbedIcon;
	private boolean editMode = false;

	public AppPieView(Context context, AttributeSet attr) {
		super(context, attr);

		Resources res = context.getResources();
		selectedPaint.setColorFilter(new PorterDuffColorFilter(
				res.getColor(R.color.selected),
				PorterDuff.Mode.SRC_IN));
		transparentBackgroundColor = res.getColor(
				R.color.background_transparent);

		iconAdd = getBitmapFromDrawable(res, R.drawable.ic_add);
		iconRemove = getBitmapFromDrawable(res, R.drawable.ic_remove);
		iconInfo = getBitmapFromDrawable(res, R.drawable.ic_info);
		iconDone = getBitmapFromDrawable(res, R.drawable.ic_done);
		dp = res.getDisplayMetrics().density;

		float touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
		touchSlopSq = touchSlop * touchSlop;
		tapTimeout = ViewConfiguration.getTapTimeout();

		surfaceHolder = getHolder();
		appMenu.indexAppsAsync(context);

		initSurfaceHolder(surfaceHolder);
		initTouchListener();

		setZOrderOnTop(true);
	}

	public void setOpenListListener(OpenListListener listener) {
		listListener = listener;
	}

	public void addIconInteractive(AppMenu.Icon appIcon, Point from) {
		editIcon(appIcon);
		touch.set(from.x, from.y);
		setCenter(viewWidth >> 1, viewHeight >> 1);
		drawView();
	}

	public boolean isEditMode() {
		return editMode;
	}

	public void endEditMode() {
		appMenu.store(getContext());
		backup.clear();
		ungrabbedIcons.clear();
		grabbedIcon = null;
		editMode = false;
		invalidateView();
		drawView();
	}

	private void editIcon(AppMenu.Icon icon) {
		backup.clear();
		backup.addAll(appMenu.icons);
		appMenu.icons.remove(icon);
		ungrabbedIcons.clear();
		ungrabbedIcons.addAll(appMenu.icons);
		grabbedIcon = icon;
		editMode = true;
		invalidateView();
	}

	private static Bitmap getBitmapFromDrawable(Resources res, int resId) {
		return Converter.getBitmapFromDrawable(getDrawable(res, resId));
	}

	private static Drawable getDrawable(Resources res, int resId) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			return res.getDrawable(resId, null);
		} else {
			//noinspection deprecation
			return res.getDrawable(resId);
		}
	}

	private void initSurfaceHolder(SurfaceHolder holder) {
		holder.setFormat(PixelFormat.TRANSPARENT);
		holder.addCallback(new SurfaceHolder.Callback() {
			@Override
			public void surfaceChanged(
					SurfaceHolder holder,
					int format,
					int width,
					int height) {
				initMenu(width, height);
				if (editMode) {
					invalidateView();
					drawView();
				}
			}

			@Override
			public void surfaceCreated(SurfaceHolder holder) {
			}

			@Override
			public void surfaceDestroyed(SurfaceHolder holder) {
			}
		});
	}

	private void initMenu(int width, int height) {
		int min = Math.min(width, height);
		float maxIconSize = 64f * dp;
		if (Math.floor(min * .28f) > maxIconSize) {
			min = Math.round(maxIconSize / .28f);
		}
		radius = Math.round(min * .5f);
		viewWidth = width;
		viewHeight = height;
		layoutTouchTargets(height > width);
	}

	private void layoutTouchTargets(boolean portrait) {
		Bitmap[] icons = new Bitmap[]{iconAdd, iconRemove, iconInfo, iconDone};
		Rect[] rects = new Rect[]{iconAddRect, iconRemoveRect, iconInfoRect, iconDoneRect};
		int length = icons.length;
		int totalWidth = 0;
		int totalHeight = 0;
		int largestWidth = 0;
		int largestHeight = 0;
		// initialize rects and calculate totals
		for (int i = 0; i < length; ++i) {
			Bitmap icon = icons[i];
			int w = icon.getWidth();
			int h = icon.getHeight();
			rects[i].set(0, 0, w, h);
			largestWidth = Math.max(largestWidth, w);
			largestHeight = Math.max(largestHeight, h);
			totalWidth += w;
			totalHeight += h;
		}
		int padding = Math.round(dp * 80f);
		if (portrait) {
			int step = Math.round(
					(float) (viewWidth - totalWidth) / (length + 1));
			int x = step;
			int y = viewHeight - largestHeight - padding;
			for (Rect rect : rects) {
				rect.offset(x, y);
				x += step + rect.width();
			}
		} else {
			int step = Math.round(
					(float) (viewHeight - totalHeight) / (length + 1));
			int x = viewWidth - largestWidth - padding;
			int y = step;
			for (Rect rect : rects) {
				rect.offset(x, y);
				y += step + rect.height();
			}
		}
	}

	private void initTouchListener() {
		setOnTouchListener(new View.OnTouchListener() {
			private Point down = new Point();
			private long downAt;

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				touch.set(Math.round(event.getRawX()),
						Math.round(event.getRawY()));
				switch (event.getActionMasked()) {
					default:
						break;
					case MotionEvent.ACTION_CANCEL:
						invalidateTouch();
						grabbedIcon = null;
						drawView();
						break;
					case MotionEvent.ACTION_MOVE:
						drawView();
						break;
					case MotionEvent.ACTION_DOWN:
						if (editMode) {
							editIconAt(touch);
						} else {
							down.set(touch.x, touch.y);
							downAt = event.getEventTime();
							setCenter(touch);
						}
						drawView();
						break;
					case MotionEvent.ACTION_UP:
						v.performClick();
						if (editMode) {
							if (iconAddRect.contains(touch.x, touch.y)) {
								if (grabbedIcon != null) {
									rollback();
								} else {
									((Activity) getContext()).onBackPressed();
								}
							} else if (iconRemoveRect.contains(
									touch.x, touch.y)) {
								if (grabbedIcon != null) {
									appMenu.icons.remove(grabbedIcon);
									invalidateView();
								}
							} else if (iconInfoRect.contains(
									touch.x, touch.y)) {
								if (grabbedIcon != null) {
									rollback();
									startAppInfo(((AppMenu.AppIcon)
											grabbedIcon).packageName);
								}
							} else if (iconDoneRect.contains(
									touch.x, touch.y)) {
								if (grabbedIcon != null) {
									rollback();
								} else {
									endEditMode();
								}
							}
							grabbedIcon = null;
						} else {
							if (SystemClock.uptimeMillis() - downAt <= tapTimeout &&
									distSq(down, touch) <= touchSlopSq) {
								if (listListener != null) {
									listListener.onOpenList();
								}
							} else {
								appMenu.launch(v.getContext());
							}
						}
						invalidateTouch();
						drawView();
						break;
				}
				return true;
			}
		});
	}

	private void rollback() {
		appMenu.icons.clear();
		appMenu.icons.addAll(backup);
	}

	private void startAppInfo(String packageName) {
		Intent intent = new Intent(
				android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
		intent.addCategory(Intent.CATEGORY_DEFAULT);
		intent.setData(Uri.parse("package:" + packageName));
		getContext().startActivity(intent);
	}

	private void setCenter(Point point) {
		setCenter(point.x, point.y);
	}

	private void setCenter(int x, int y) {
		appMenu.set(
				Math.max(radius, Math.min(viewWidth - radius, x)),
				Math.max(radius, Math.min(viewHeight - radius, y)),
				radius);
	}

	private void editIconAt(Point point) {
		for (int i = 0, size = appMenu.icons.size(); i < size; ++i) {
			AppMenu.Icon icon = appMenu.icons.get(i);
			float sizeSq = Math.round(icon.size * icon.size);
			if (distSq(point.x, point.y, icon.x, icon.y) < sizeSq) {
				editIcon(icon);
				break;
			}
		}
	}

	private void drawView() {
		if (touch.equals(lastTouch)) {
			return;
		}
		Canvas canvas = surfaceHolder.lockCanvas();
		if (canvas == null) {
			return;
		}
		if (editMode) {
			canvas.drawColor(transparentBackgroundColor,
					PorterDuff.Mode.SRC);
		} else {
			canvas.drawColor(0, PorterDuff.Mode.CLEAR);
		}
		if (shouldShowMenu() || editMode) {
			if (editMode) {
				boolean hasIcon = grabbedIcon != null;
				drawIcon(canvas, iconAdd, iconAddRect, !hasIcon);
				drawIcon(canvas, iconRemove, iconRemoveRect, hasIcon);
				drawIcon(canvas, iconInfo, iconInfoRect, hasIcon);
				drawIcon(canvas, iconDone, iconDoneRect, !hasIcon);
			}
			if (grabbedIcon != null) {
				int size = ungrabbedIcons.size();
				double step = AppMenu.TAU / (size + 1);
				double angle = AppMenu.getPositiveAngle(Math.atan2(
						touch.y - appMenu.getCenterY(),
						touch.x - appMenu.getCenterX()) + step * .5);
				int insertAt = (int) Math.floor(angle / step);
				appMenu.icons.clear();
				appMenu.icons.addAll(ungrabbedIcons);
				appMenu.icons.add(Math.min(size, insertAt), grabbedIcon);
				appMenu.calculate(touch.x, touch.y);
				grabbedIcon.x = touch.x;
				grabbedIcon.y = touch.y;
			} else if (editMode) {
				appMenu.calculate(appMenu.getCenterX(), appMenu.getCenterY());
			} else {
				appMenu.calculate(touch.x, touch.y);
			}
			appMenu.draw(canvas);
		}
		lastTouch.set(touch.x, touch.y);
		surfaceHolder.unlockCanvasAndPost(canvas);
	}

	private void invalidateView() {
		lastTouch.set(-2, -2);
	}

	private void invalidateTouch() {
		touch.set(-1, -1);
	}

	private boolean shouldShowMenu() {
		return touch.x > -1;
	}

	private void drawIcon(Canvas canvas, Bitmap icon, Rect rect,
			boolean active) {
		canvas.drawBitmap(icon, null, rect,
				active && rect.contains(touch.x, touch.y)
						? selectedPaint
						: bitmapPaint);
	}

	private static float distSq(Point a, Point b) {
		return distSq(a.x, a.y, b.x, b.y);
	}

	private static float distSq(int ax, int ay, int bx, int by) {
		float dx = ax - bx;
		float dy = ay - by;
		return dx*dx + dy*dy;
	}
}
