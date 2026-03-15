# WebP Conversion Instructions

## Step 1: Install WebP Tools

### Option A: Download Prebuilt (Easiest)
1. Download from: https://storage.googleapis.com/downloads.webmproject.org/releases/webp/libwebp-1.3.2-windows-x64.zip
2. Extract the ZIP file
3. Copy `cwebp.exe` from the `bin` folder to your project root folder (same location as `convert_to_webp.ps1`)

### Option B: Chocolatey (If you have it)
```powershell
choco install webp
```

## Step 2: Run the Conversion Script

1. Open PowerShell in the project root directory
2. Run the script:
```powershell
.\convert_to_webp.ps1
```

**Note:** If you get a security error, you may need to allow script execution:
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

## Step 3: Verify

The script will:
- ✅ Convert all `.png` and `.jpg` files to `.webp` in both `english_cards` and `chinese_cards` folders
- ✅ Skip `SFD-T01.webp` and `SFD-TO2.webp` (already WebP)
- ✅ Delete original PNG/JPG files after successful conversion
- ✅ Show progress and summary

## Step 4: Test the App

1. Sync Gradle (if needed)
2. Run the app
3. Test loading cards from all three sets (OGN, OGS, SFD)
4. Verify both English and Chinese cards load correctly

## Expected Results

- **Before:** ~484MB APK with PNG files
- **After:** ~120-150MB APK with WebP files (70-75% size reduction!)
- **Quality:** Visually identical at 85% quality setting
- **Performance:** Faster loading due to smaller file sizes

## Rollback (If Needed)

If something goes wrong, you can:
1. Restore from git: `git checkout -- app/src/main/assets/`
2. Or manually re-add the original PNG/JPG files

## Notes

- WebP is natively supported on Android 4.0+ (API 14+)
- Your app targets API 24+, so full support guaranteed
- The conversion uses quality 85 (good balance of size vs quality)
- You can adjust quality in the script by changing `-q 85` to `-q 90` (higher quality, larger size) or `-q 80` (lower quality, smaller size)
