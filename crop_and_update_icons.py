
import os
from PIL import Image

# Config
SOURCE_PATH = r"C:/Users/Raina/.gemini/antigravity/brain/079a30ee-2a3c-4e53-b23d-c8933d7ca409/uploaded_image_0_1765734902717.jpg"
CROPPED_PATH = r"C:/Users/Raina/.gemini/antigravity/brain/079a30ee-2a3c-4e53-b23d-c8933d7ca409/cropped_logo.png"
RES_DIR = r"c:/Users/Raina/OneDrive/Desktop/GuideLens App/app/src/main/res"

ICON_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

def crop_and_generate():
    if not os.path.exists(SOURCE_PATH):
        print(f"Error: Source image not found at {SOURCE_PATH}")
        return

    try:
        # 1. Load Image
        img = Image.open(SOURCE_PATH)
        w, h = img.size
        print(f"Original Size: {w}x{h}")
        
        # 2. Crop Center
        # Heuristic: The logo is likely in the visual center.
        # "GUIDE (*) LENS" - broadly centered.
        # Let's crop a square. Width is 462.
        # The logo is probably smaller than full width. Let's try 180x180 px from center.
        # Adjust Y if needed. Splash screen logos are often slightly above optical center, but exact center is a safe bet for a crop.
        
        crop_size = 112 # Tighter crop for "exact copy" of the symbol with outline
        left = (w - crop_size) / 2
        top = (h - crop_size) / 2
        right = (w + crop_size) / 2
        bottom = (h + crop_size) / 2
        
        cropped_img = img.crop((left, top, right, bottom))
        
        # Save cropped for verification/usage
        cropped_img.save(CROPPED_PATH, "PNG")
        print(f"Saved cropped logo to {CROPPED_PATH}")

        # 3. Generate Icons
        # Ensure RGBA
        icon_source = cropped_img.convert("RGBA")
        
        # Optional: Make it circular masking? 
        # The user just said "crop it", but app icons are usually square (with full bleed) or round.
        # Standard Android adaptive icons are complicated. For now, just resizing the square crop is what was requested.
        
        for folder, size in ICON_SIZES.items():
            target_dir = os.path.join(RES_DIR, folder)
            if not os.path.exists(target_dir):
                os.makedirs(target_dir)

            # High quality resize
            resized_icon = icon_source.resize((size, size), Image.Resampling.LANCZOS)
            
            # Save
            icon_path = os.path.join(target_dir, "ic_launcher.png")
            resized_icon.save(icon_path, "PNG")
            
            round_icon_path = os.path.join(target_dir, "ic_launcher_round.png")
            resized_icon.save(round_icon_path, "PNG")
            
            print(f"Generated {size}x{size} icons in {folder}")

        print("✅ Icons regenerated from cropped logo!")

    except Exception as e:
        print(f"❌ Failed: {e}")

if __name__ == "__main__":
    crop_and_generate()
