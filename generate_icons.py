
import os
from PIL import Image

# Configuration
SOURCE_IMAGE_PATH = r"C:/Users/Raina/.gemini/antigravity/brain/079a30ee-2a3c-4e53-b23d-c8933d7ca409/uploaded_image_1765736738843.png"
RES_DIR = r"c:/Users/Raina/OneDrive/Desktop/GuideLens App/app/src/main/res"

ICON_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

def generate_icons():
    if not os.path.exists(SOURCE_IMAGE_PATH):
        print(f"Error: Source image not found at {SOURCE_IMAGE_PATH}")
        return

    try:
        img = Image.open(SOURCE_IMAGE_PATH)
        # Ensure RGBA for transparency support
        img = img.convert("RGBA")
        
        print(f"Loaded source image: {img.size}")

        for folder, size in ICON_SIZES.items():
            target_dir = os.path.join(RES_DIR, folder)
            if not os.path.exists(target_dir):
                os.makedirs(target_dir)
                print(f"Created directory: {target_dir}")

            # Resize image
            # Lanczos is high-quality downsampling
            resized_img = img.resize((size, size), Image.Resampling.LANCZOS)
            
            # Save as standard square icon
            icon_path = os.path.join(target_dir, "ic_launcher.png")
            resized_img.save(icon_path, "PNG")
            
            # Save as round icon (simple resize for now, proper adaptive icons need xml)
            # For this request, we are just overwriting the pngs which is the standard request
            round_icon_path = os.path.join(target_dir, "ic_launcher_round.png")
            resized_img.save(round_icon_path, "PNG")
            
            print(f"Generated {size}x{size} icons in {folder}")

        print("✅ App icons updated successfully!")

    except Exception as e:
        print(f"❌ Failed to generate icons: {e}")

if __name__ == "__main__":
    generate_icons()
