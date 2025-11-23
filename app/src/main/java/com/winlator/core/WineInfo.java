package com.winlator.core;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;

import com.winlator.contents.ContentProfile;
import com.winlator.contents.ContentsManager;
import com.winlator.xenvironment.ImageFs;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.gamenative.R;

public class WineInfo implements Parcelable {

    public static final WineInfo MAIN_WINE_VERSION = new WineInfo("wine", "9.2", "x86_64");
    private static final Pattern pattern = Pattern.compile("^(wine|proton|Proton)\\-([0-9]+(?:\\.[0-9]+)*)\\-?([0-9\\.]+)?\\-(x86|x86_64|arm64ec)$");
    public final String version;
    public final String type;
    public String subversion;
    public final String path;
    public String libPath; // Path to lib directory from profile.json (e.g., "lib" or "lib/wine")
    private String arch;

    public WineInfo(String type, String version, String arch) {
        this.type = type;
        this.version = version;
        this.subversion = null;
        this.arch = arch;
        this.path = null;
        this.libPath = null;
    }

    public WineInfo(String type, String version, String subversion, String arch, String path) {
        this.type = type;
        this.version = version;
        this.subversion = subversion != null && !subversion.isEmpty() ? subversion : null;
        this.arch = arch;
        this.path = path;
        this.libPath = null;
    }

    public WineInfo(String type, String version, String arch, String path) {
        this.type = type;
        this.version = version;
        this.arch = arch;
        this.path = path;
        this.libPath = null;
    }

    private WineInfo(Parcel in) {
        type = in.readString();
        version = in.readString();
        subversion = in.readString();
        arch = in.readString();
        path = in.readString();
        libPath = in.readString();
    }

    public String getArch() {
        return arch;
    }

    public void setArch(String arch) {
        this.arch = arch;
    }

    public boolean isWin64() {
        return arch.equals("x86_64") || arch.equals("arm64ec");
    }

    public boolean isArm64EC() {
        return arch.equals("arm64ec");
    }

    public boolean isMainWineVersion() {
        WineInfo other = WineInfo.MAIN_WINE_VERSION;

        boolean pathMatches
                = (path == null && other.path == null)
                || (path != null && path.equals(other.path));

        return type.equals(other.type)
                && version.equals(other.version)
                && arch.equals(other.arch)
                && pathMatches;
    }

    public String getExecutable(Context context, boolean wow64Mode) {
        if (this == MAIN_WINE_VERSION) {
            File wineBinDir = new File(ImageFs.find(context).getRootDir(), "/opt/wine/bin");
            File wineBinFile = new File(wineBinDir, "wine");
            File winePreloaderBinFile = new File(wineBinDir, "wine-preloader");
            FileUtils.copy(new File(wineBinDir, wow64Mode ? "wine-wow64" : "wine32"), wineBinFile);
            FileUtils.copy(new File(wineBinDir, wow64Mode ? "wine-preloader-wow64" : "wine32-preloader"), winePreloaderBinFile);
            FileUtils.chmod(wineBinFile, 0771);
            FileUtils.chmod(winePreloaderBinFile, 0771);
            return wow64Mode ? "wine" : "wine64";
        } else {
            return (new File(path, "/bin/wine64")).isFile() ? "wine64" : "wine";
        }
    }

    public String identifier() {
        if (type.equals("proton")) {
            return "proton-" + fullVersion() + "-" + arch;
        } else {
            return "wine-" + fullVersion() + "-" + arch;
        }
    }

    public String fullVersion() {
        return version + (subversion != null ? "-" + subversion : "");
    }

    @NonNull
    @Override
    public String toString() {
        if (type.equals("proton")) {
            return "Proton " + fullVersion() + (this == MAIN_WINE_VERSION ? " (Custom)" : "");
        } else {
            return "Wine " + fullVersion() + (this == MAIN_WINE_VERSION ? " (Custom)" : "");
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<WineInfo> CREATOR = new Parcelable.Creator<WineInfo>() {
        public WineInfo createFromParcel(Parcel in) {
            return new WineInfo(in);
        }

        public WineInfo[] newArray(int size) {
            return new WineInfo[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(type);
        dest.writeString(version);
        dest.writeString(subversion);
        dest.writeString(arch);
        dest.writeString(path);
        dest.writeString(libPath);
    }

    @NonNull
    public static WineInfo fromIdentifier(Context context, ContentsManager contentsManager, String identifier) {
        Log.d("WineInfo", "🍷 fromIdentifier called with: '" + identifier + "'");

        // Handle main Wine version
        if (identifier.equals(MAIN_WINE_VERSION.identifier())) {
            Log.d("WineInfo", "   ✅ Matched MAIN_WINE_VERSION");
            return new WineInfo(MAIN_WINE_VERSION.type, MAIN_WINE_VERSION.version, MAIN_WINE_VERSION.arch, null);
        }

        // Try to find profile using multiple lookup strategies
        ContentProfile wineProfile = findWineProfile(contentsManager, identifier);
        if (wineProfile != null) {
            Log.d("WineInfo", "   ✅ Found profile: type=" + wineProfile.type + ", verName=" + wineProfile.verName + ", verCode=" + wineProfile.verCode);
        } else {
            Log.w("WineInfo", "   ⚠️ No profile found for identifier: '" + identifier + "'");
        }

        // Parse identifier components using regex
        Matcher matcher = pattern.matcher(identifier);
        if (!matcher.find()) {
            Log.w("WineInfo", "   ❌ Identifier doesn't match pattern, falling back to main Wine");
            // Identifier doesn't match expected pattern, fall back to main Wine
            return new WineInfo(MAIN_WINE_VERSION.type, MAIN_WINE_VERSION.version, MAIN_WINE_VERSION.arch, null);
        }

        String type = matcher.group(1);
        String version = matcher.group(2);
        String arch = matcher.group(4);
        String path = "";

        // Check if it's a built-in bionic Wine version
        if (wineProfile == null) {
            Log.d("WineInfo", "   No profile found, checking built-in bionic versions");
            ImageFs imageFs = ImageFs.find(context);
            String[] wineVersions = context.getResources().getStringArray(R.array.bionic_wine_entries);
            for (String wineVersion : wineVersions) {
                if (wineVersion.contains(identifier)) {
                    path = imageFs.getRootDir().getPath() + "/opt/" + identifier;
                    Log.d("WineInfo", "   ✅ Found built-in version at: " + path);
                    break;
                }
            }
            Log.d("WineInfo", "   Returning WineInfo: type=" + type + ", version=" + version + ", arch=" + arch + ", path=" + path);
            return new WineInfo(type, version, arch, path);
        }

        // Use imported Wine/Proton profile
        if (wineProfile.type == ContentProfile.ContentType.CONTENT_TYPE_WINE
                || wineProfile.type == ContentProfile.ContentType.CONTENT_TYPE_PROTON) {
            path = ContentsManager.getInstallDir(context, wineProfile).getPath();
            Log.d("WineInfo", "   ✅ Using imported profile path: " + path);
            WineInfo wineInfo = new WineInfo(type, version, arch, path);
            wineInfo.libPath = wineProfile.wineLibPath;
            return wineInfo;
        }

        Log.d("WineInfo", "   Returning default WineInfo: type=" + type + ", version=" + version + ", arch=" + arch);
        return new WineInfo(type, version, arch, path);
    }

    /**
     * Attempts to find a Wine/Proton profile using multiple lookup strategies.
     * Tries various identifier formats to handle different naming conventions.
     */
    private static ContentProfile findWineProfile(ContentsManager contentsManager, String identifier) {
        Log.d("WineInfo", "   🔍 findWineProfile searching for: '" + identifier + "'");
        ContentProfile profile;

        // Try: identifier-0 (with version code 0)
        Log.d("WineInfo", "      Trying: '" + identifier + "-0'");
        profile = contentsManager.getProfileByEntryName(identifier + "-0");
        if (profile != null) {
            Log.d("WineInfo", "      ✅ Found with -0 suffix");
            return profile;
        }

        // Try: identifier as-is
        Log.d("WineInfo", "      Trying: '" + identifier + "' (as-is)");
        profile = contentsManager.getProfileByEntryName(identifier);
        if (profile != null) {
            Log.d("WineInfo", "      ✅ Found as-is");
            return profile;
        }

        // Try: capitalized identifier with version codes 0-10
        if (identifier.startsWith("proton-") || identifier.startsWith("wine-")) {
            String capitalizedIdentifier = Character.toUpperCase(identifier.charAt(0)) + identifier.substring(1);
            Log.d("WineInfo", "      Trying capitalized: '" + capitalizedIdentifier + "' with verCodes 0-10");
            for (int verCode = 0; verCode <= 10; verCode++) {
                profile = contentsManager.getProfileByEntryName(capitalizedIdentifier + "-" + verCode);
                if (profile != null) {
                    Log.d("WineInfo", "      ✅ Found with capitalized + verCode " + verCode);
                    return profile;
                }
            }
        }

        // Try: dots replaced with dashes
        if (identifier.contains(".")) {
            String identifierWithDashes = identifier.replace('.', '-');
            Log.d("WineInfo", "      Trying dots→dashes: '" + identifierWithDashes + "'");
            profile = contentsManager.getProfileByEntryName(identifierWithDashes + "-0");
            if (profile != null) {
                Log.d("WineInfo", "      ✅ Found with dots→dashes + -0");
                return profile;
            }

            profile = contentsManager.getProfileByEntryName(identifierWithDashes);
            if (profile != null) {
                Log.d("WineInfo", "      ✅ Found with dots→dashes");
                return profile;
            }
        }

        Log.d("WineInfo", "      ❌ No match found in any strategy");
        return null;
    }

    public static boolean isMainWineVersion(String wineVersion) {
        return wineVersion == null || wineVersion.equals(MAIN_WINE_VERSION.identifier());
    }
}
