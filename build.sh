#!/usr/bin/env python3
"""
WallPix build — two APKs (Admin + User) from one codebase.
Pipeline: aapt2 -> javac -> d8 -> apksigner

Usage:
    python3 build.sh          # build both
    python3 build.sh admin    # only the admin APK
    python3 build.sh user     # only the user APK

Env:
    ANDROID_JAR   path to android.jar (default: libs/android.jar)
    KEYSTORE      path to signing key   (default: signing/wallpix.jks)
"""
import os
import shutil
import subprocess
import sys
import zipfile

BASE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(BASE, "src")
RES = os.path.join(BASE, "res")
BUILD = os.path.join(BASE, "build")
DIST = os.path.join(BASE, "dist")
ANDROID_JAR = os.environ.get("ANDROID_JAR", os.path.join(BASE, "libs", "android.jar"))
KEYSTORE = os.environ.get("KEYSTORE", os.path.join(BASE, "signing", "wallpix.jks"))
KEY_ALIAS = "wallpix"
KEY_PASSWORD = "wallpix123"
VERSION_CODE = "2"
VERSION_NAME = "1.1.0"

VARIANTS = {
    "admin": ("manifest/AndroidManifest-admin.xml", "WallPix-Admin-v1.1.apk"),
    "user": ("manifest/AndroidManifest-user.xml", "WallPix-User-v1.1.apk"),
}


def die(msg):
    print("ERROR: " + msg, file=sys.stderr)
    sys.exit(1)


def run(cmd, **kw):
    print("> " + " ".join(cmd) if isinstance(cmd, list) else cmd)
    r = subprocess.run(cmd, **kw)
    if r.returncode != 0:
        die("Command failed: " + (" ".join(cmd) if isinstance(cmd, list) else cmd))
    return r


def collect_java():
    out = []
    for root, _, files in os.walk(SRC):
        for f in files:
            if f.endswith(".java"):
                out.append(os.path.join(root, f))
    return out


def main():
    for tool in ["aapt2", "javac", "d8", "apksigner", "jar"]:
        if shutil.which(tool) is None:
            die(tool + " not found in PATH")
    if not os.path.exists(ANDROID_JAR):
        die("android.jar missing: " + ANDROID_JAR)

    requested = sys.argv[1] if len(sys.argv) > 1 else "both"
    if requested == "both":
        names = ["admin", "user"]
    elif requested in VARIANTS:
        names = [requested]
    else:
        die("unknown variant: " + requested + " (use admin / user / both)")

    if not os.path.exists(KEYSTORE):
        os.makedirs(os.path.dirname(KEYSTORE), exist_ok=True)
        print("\n--- Creating signing key ---")
        run(["keytool", "-genkeypair", "-keystore", KEYSTORE, "-alias", KEY_ALIAS,
             "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
             "-storepass", KEY_PASSWORD, "-keypass", KEY_PASSWORD,
             "-dname", "CN=WallPix, OU=AntiGem, O=WallPix, C=IN"])

    if os.path.exists(BUILD):
        shutil.rmtree(BUILD)
    os.makedirs(BUILD)
    os.makedirs(DIST, exist_ok=True)

    print("\n--- Step 1: compile resources ---")
    res_zip = os.path.join(BUILD, "res.zip")
    run(["aapt2", "compile", "--dir", RES, "-o", res_zip])

    print("\n--- Step 2: link manifest (admin) and generate R.java ---")
    gen_dir = os.path.join(BUILD, "gen")
    os.makedirs(gen_dir)
    admin_manifest = os.path.join(BASE, VARIANTS["admin"][0])
    admin_base = os.path.join(BUILD, "base-admin.apk")
    run(["aapt2", "link", "-o", admin_base,
         "-I", ANDROID_JAR,
         "--manifest", admin_manifest,
         "--java", gen_dir,
         "--custom-package", "com.wallpix.app",
         "--auto-add-overlay",
         "--min-sdk-version", "26",
         "--target-sdk-version", "34",
         "--version-code", VERSION_CODE,
         "--version-name", VERSION_NAME,
         res_zip])

    r_java = os.path.join(gen_dir, "com", "wallpix", "app", "R.java")
    if not os.path.exists(r_java):
        die("Generated R.java not found")

    print("\n--- Step 3: compile java ---")
    classes_dir = os.path.join(BUILD, "classes")
    os.makedirs(classes_dir)
    java_files = collect_java() + [r_java]
    print("Compiling %d java files" % len(java_files))
    run(["javac", "--release", "8", "-classpath", ANDROID_JAR,
         "-d", classes_dir, "-encoding", "UTF-8", "-nowarn"] + java_files)

    print("\n--- Step 4: dex (d8) ---")
    dex_dir = os.path.join(BUILD, "dex")
    os.makedirs(dex_dir)
    classes_jar = os.path.join(BUILD, "classes.jar")
    run(["jar", "cf", classes_jar, "-C", classes_dir, "."])
    run(["d8", "--release", "--min-api", "26", "--output", dex_dir, classes_jar])
    dex_file = os.path.join(dex_dir, "classes.dex")
    if not os.path.exists(dex_file):
        die("classes.dex was not produced")

    print("\n--- Step 5: link / package / sign ---")
    for name in names:
        manifest_rel, out_name = VARIANTS[name]
        manifest = os.path.join(BASE, manifest_rel)
        if not os.path.exists(manifest):
            die("manifest missing: " + manifest)

        base_apk = admin_base if name == "admin" else os.path.join(BUILD, "base-user.apk")
        if name != "admin":
            run(["aapt2", "link", "-o", base_apk,
                 "-I", ANDROID_JAR,
                 "--manifest", manifest,
                 "--custom-package", "com.wallpix.app",
                 "--auto-add-overlay",
                 "--min-sdk-version", "26",
                 "--target-sdk-version", "34",
                 "--version-code", VERSION_CODE,
                 "--version-name", VERSION_NAME,
                 res_zip])

        with zipfile.ZipFile(base_apk, "a", zipfile.ZIP_DEFLATED) as z:
            z.write(dex_file, "classes.dex")

        out = os.path.join(DIST, out_name)
        run(["apksigner", "sign", "--ks", KEYSTORE, "--ks-key-alias", KEY_ALIAS,
             "--ks-pass", "pass:" + KEY_PASSWORD, "--out", out, base_apk])
        run(["apksigner", "verify", out])
        size = os.path.getsize(out) / 1024.0
        print("\nOK: %s (%.1f KB)" % (out, size))
        run(["aapt2", "dump", "badging", out])

    print("\nDone. APKs in: " + DIST)


if __name__ == "__main__":
    main()
