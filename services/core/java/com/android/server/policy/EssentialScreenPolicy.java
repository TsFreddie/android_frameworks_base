package com.android.server.policy;


import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;
import android.view.WindowManager.LayoutParams;
import android.view.WindowManagerPolicy.WindowManagerFuncs;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class EssentialScreenPolicy
{
    private static final boolean DEBUG = true;
    private static final String KEY_BYPASS_PORTRAIT = "ESSENTIAL_BYPASS_PORTRAIT";
    private static final String KEY_BYPASS_WHITELIST = "ESSENTIAL_BYPASS_WHITELIST";
    private static final String KEY_FULL_LAYOUT_WHITELIST = "ESSENTIAL_LAYOUT_WHITELIST";
    private static final String RESOUCES_PKG = "com.essential.resources";
    private static final String TAG = EssentialScreenPolicy.class.getSimpleName();
    private static final String WILDCARD_ALL = "*";
    private static final String WILDCARD_NONE = "-";
    private static Set<String> sBypassPackagesWhitelist;
    private static String sBypassPackagesWhitelistString;
    private static Set<String> sBypassPortraitWhitelist;
    private static String sBypassPortraitWhitelistString;
    private static int sCameraNotchInset;
    private static Context sContext;
    private static Set<String> sFullLayoutPackagesWhitelist;
    private static String sFullLayoutPackagesWhitelistString;
    private static int sStatusBarHeight;

    private static boolean bypass(LayoutParams attrs, int rotation)
    {
        boolean mBypass = false;
        if (!contains(sBypassPackagesWhitelist, attrs))
        {
            if (rotation == 0 || rotation == 2)
            {
                mBypass = contains(sBypassPortraitWhitelist, attrs);
            }
        }
        else {
            mBypass = true;
        }
        return mBypass;
    }

    private static boolean contains(Set<String> set, LayoutParams layoutParams)
    {
        boolean mContains = false;
        if (set != null) {
            if (!set.contains(WILDCARD_NONE)) {
                if (!set.contains(WILDCARD_ALL)) {
                    if (layoutParams.packageName != null) {
                        mContains = set.contains(layoutParams.packageName);
                    }
                }
                else {
                    mContains = true;
                }
            }
        }
        return mContains;
    }

    public static int getAspectCompatHeight(int height, int rotation)
    {
        switch (rotation)
        {
            case 0:
               return height - sStatusBarHeight;
            case 2:
               return height - sCameraNotchInset;
            default:
               return height;

        }
    }

    public static int getAspectCompatWidth(int width, int rotation)
    {
        if (rotation == 1 || rotation == 3)
        {
            return width = sCameraNotchInset;
        }
        else {
            return width;
        }
    }

    public static Rect getDisplayFrameInsets(final LayoutParams attrs, final int sysUiFl, final int fl, final int rotation) {
        final Rect rect = new Rect();
        if (!bypass(attrs, rotation)) {
            switch (rotation) {
                case 0: {
                    if (insetTop(attrs, sysUiFl, fl)) {
                        rect.top = EssentialScreenPolicy.sStatusBarHeight;
                        break;
                    }
                    break;
                }
                case 1: {
                    rect.left = EssentialScreenPolicy.sCameraNotchInset;
                    break;
                }
                case 2: {
                    if (insetBottom(attrs, sysUiFl, fl)) {
                        rect.bottom = EssentialScreenPolicy.sCameraNotchInset;
                        break;
                    }
                    break;
                }
                case 3: {
                    rect.right = EssentialScreenPolicy.sCameraNotchInset;
                    break;
                }
            }
        }
        return rect;
    }

    private static String[] getStrings(final Context context, final String whitelist) {
        try {
            final Resources resourcesForApplication = context.getPackageManager().getResourcesForApplication("com.essential.resources");
            return resourcesForApplication.getStringArray(resourcesForApplication.getIdentifier(whitelist, "array", "com.essential.resources"));
        }
        catch (NameNotFoundException ex) {
            ex.printStackTrace();
            return new String[0];
        }
    }

    public static void init(final Context context, final Handler handler, final WindowManagerFuncs wm) {
        if (sContext != null) {
            return;
        }
        sContext = context;
        sStatusBarHeight = sContext.getResources().getDimensionPixelSize(17104922);
        sCameraNotchInset = sContext.getResources().getDimensionPixelSize(17105093);
        reloadFromSetting(sContext);
        final ContentObserver contentObserver = new ContentObserver(handler) {
            public void onChange(final boolean b) {
                synchronized (wm.getWindowManagerLock()) {
                    reloadFromSetting(sContext);
                }
            }
        };
        final ContentResolver contentResolver = context.getContentResolver();
        contentResolver.registerContentObserver(Settings.Global.getUriFor("ESSENTIAL_BYPASS_WHITELIST"), false, (ContentObserver)contentObserver, -1);
        contentResolver.registerContentObserver(Settings.Global.getUriFor("ESSENTIAL_BYPASS_PORTRAIT"), false, (ContentObserver)contentObserver, -1);
        contentResolver.registerContentObserver(Settings.Global.getUriFor("ESSENTIAL_LAYOUT_WHITELIST"), false, (ContentObserver)contentObserver, -1);
        final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            public void onReceive(Context data, final Intent intent) {
                Uri uri = intent.getData();
                if (uri == null || !"com.essential.resources".equals(uri.getSchemeSpecificPart())) {
                    return;
                }
                synchronized (wm.getWindowManagerLock()) {
                    reloadFromSetting(sContext);
                }
            }
        };
        final IntentFilter intentFilter = new IntentFilter("android.intent.action.PACKAGE_REPLACED");
        intentFilter.addDataScheme("package");
        context.registerReceiver((BroadcastReceiver)broadcastReceiver, intentFilter);
    }

    private static boolean insetBottom(final LayoutParams attrs, final int sysUiFl, final int fl) {
        boolean mInset = true;
        if ((sysUiFl & 0x2) == 0x0) {
            if ((sysUiFl & 0x200) != 0x0) {
                if (contains(sFullLayoutPackagesWhitelist, attrs)) {
                    mInset = false;
                }
            }
            else {
                mInset = false;
            }
        }
        return mInset;
    }

    private static boolean insetTop(final LayoutParams attrs, final int sysUiFl, final int fl) {
        boolean mInset = false;

        if ((sysUiFl & 0x4) != 0x0 || (fl & 0x400) != 0x0) {
            mInset = true;
        }
        else {
            if ((sysUiFl & 0x400) != 0x0) {
                if (!contains(sFullLayoutPackagesWhitelist, attrs)) {
                    mInset = true;
                }
            }
        }
        return mInset;
    }

    private static Set<String> parse(String commaSeparatedValues) {
        int i = 0;
        if (commaSeparatedValues != null) {
            if (commaSeparatedValues.equals(WILDCARD_ALL)) {
                return Collections.singleton(WILDCARD_ALL);
            }
            if (commaSeparatedValues.equals(WILDCARD_NONE)) {
                return Collections.singleton(WILDCARD_NONE);
            }
            final String[] split = commaSeparatedValues.split(",");
            if (split.length > 0) {
                final HashSet<String> set = new HashSet<String>(split.length);
                while (i < split.length) {
                    commaSeparatedValues = split[i].trim();
                    if (!commaSeparatedValues.isEmpty()) {
                        set.add(commaSeparatedValues);
                    }
                    i++;
                }
                return set;
            }
        }
        return Collections.emptySet();
    }

  private static void reloadFromSetting(Context context)
  {
    Slog.d(TAG, "reloadFromSetting()");
    String value = null;
    try {
        value = Settings.Global.getStringForUser(context.getContentResolver(),
                KEY_BYPASS_WHITELIST,
                UserHandle.USER_CURRENT);

    } catch (Throwable t) {
        Slog.w(TAG, "Error loading bypass white list, value=" + value, t);
    }
    setBypassPackagesWhitelist(context, value);

    value = null;

    try {
        value = Settings.Global.getStringForUser(context.getContentResolver(),
                KEY_BYPASS_PORTRAIT,
                UserHandle.USER_CURRENT);

    } catch (Throwable t) {
        Slog.w(TAG, "Error loading bypass portrait  list, value=" + value, t);
    }
    setBypassPortraitWhitelist(context, value);

    value = null;

    try {
        value = Settings.Global.getStringForUser(context.getContentResolver(),
                KEY_FULL_LAYOUT_WHITELIST,
                UserHandle.USER_CURRENT);

    } catch (Throwable t) {
        Slog.w(TAG, "Error loading full layout white list, value=" + value, t);
    }
    setFullLayoutPackagesWhitelist(context, value);
  }

    private static void setBypassPackagesWhitelist(final Context context, final String bypassPackagesWhitelist) {
        if (bypassPackagesWhitelist == null) {
            final String[] strings = getStrings(context, "essential_bypass_whitelist");
            sBypassPackagesWhitelist = new HashSet<String>();
            sBypassPackagesWhitelistString = null;
            Slog.d(EssentialScreenPolicy.TAG, "loading whitelist from res: " + Arrays.toString(strings));
            return;
        }
        if (bypassPackagesWhitelist.equals(sBypassPackagesWhitelistString)) {
            return;
        }
        sBypassPackagesWhitelistString = bypassPackagesWhitelist;
        sBypassPackagesWhitelist = parse(bypassPackagesWhitelist);
    }

    private static void setBypassPortraitWhitelist(final Context context, final String bypassPortraitWhitelist) {
        if (bypassPortraitWhitelist == null) {
            final String[] strings = getStrings(context, "essential_bypass_portrait_whitelist");
            sBypassPortraitWhitelist = new HashSet<String>();
            sBypassPortraitWhitelistString = null;
            Slog.d(EssentialScreenPolicy.TAG, "loading whitelist from res: " + Arrays.toString(strings));
            return;
        }
        if (bypassPortraitWhitelist.equals(sBypassPortraitWhitelistString)) {
            return;
        }
        sBypassPortraitWhitelistString = bypassPortraitWhitelist;
        sBypassPortraitWhitelist = parse(bypassPortraitWhitelist);
    }


    private static void setFullLayoutPackagesWhitelist(Context context, String fullLayoutPackagesWhitelist)
    {
        if (fullLayoutPackagesWhitelist == null) {
            final String[] strings = getStrings(context, "essential_bypass_portrait_whitelist");
            sFullLayoutPackagesWhitelist = new HashSet<String>();
            sFullLayoutPackagesWhitelistString = null;
            Slog.d(EssentialScreenPolicy.TAG, "loading whitelist from res: " + Arrays.toString(strings));
            return;
        }
        if (fullLayoutPackagesWhitelist.equals(sFullLayoutPackagesWhitelistString)) {
            return;
        }
        sFullLayoutPackagesWhitelistString = fullLayoutPackagesWhitelist;
        sFullLayoutPackagesWhitelist = parse(fullLayoutPackagesWhitelist);
    }
}
