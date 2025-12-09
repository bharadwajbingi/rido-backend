from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.chrome.service import Service
from webdriver_manager.chrome import ChromeDriverManager
import time

# START BROWSER
driver = webdriver.Chrome(service=Service(ChromeDriverManager().install()))
driver.maximize_window()

# ----------------------
# ENTER YOUR COURSE URL
# ----------------------
driver.get("https://YOUR-COURSE-URL.com")

def wait(seconds=2):
    time.sleep(seconds)

def is_quiz_page():
    """Detect if current page is a quiz by checking for question blocks."""
    try:
        driver.find_element(By.CSS_SELECTOR, ".qtext, .question, .quiz")
        return True
    except:
        return False

def play_video_if_exists():
    """Checks for a video element and applies autoplay, mute, and 2x speed."""
    try:
        video = driver.find_element(By.TAG_NAME, "video")
        
        # Play video
        driver.execute_script("arguments[0].play();", video)
        wait()

        # Mute video
        driver.execute_script("arguments[0].muted = true;", video)

        # Set speed to 2x
        driver.execute_script("arguments[0].playbackRate = 2.0;", video)

        print("🎬 Video found → Playing at 2x speed (muted).")

        # Wait until video ends (polling)
        while True:
            ended = driver.execute_script("return arguments[0].ended;", video)
            if ended:
                print("✔ Video finished.")
                break
            wait(1)

        return True

    except Exception as e:
        print("❌ No video found on this page.")
        return False


def go_to_next_lesson():
    """Clicks next lesson or next button."""
    try:
        next_btn = driver.find_element(By.XPATH, "//button[contains(., 'Next') or contains(., 'next')]")
        next_btn.click()
        print("➡ Moving to next lesson.")
    except:
        try:
            # Secondary selector (course navigation)
            driver.find_element(By.CSS_SELECTOR, ".next-activity, .next-btn, .nav-next").click()
            print("➡ Moving to next lesson.")
        except:
            print("⚠ Could not find Next button. Stopping.")


# -------------------------------
# MAIN LOOP — Repeats until course ends
# -------------------------------

while True:
    wait(3)

    if is_quiz_page():
        print("🚫 Quiz detected → Skipping to next video.")
        go_to_next_lesson()
        continue

    video_played = play_video_if_exists()

    if video_played:
        go_to_next_lesson()
    else:
        print("⚠ Page has no video and is not a quiz. Moving next.")
        go_to_next_lesson()
