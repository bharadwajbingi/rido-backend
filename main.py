import pyautogui
import time

# Wait 5 seconds before typing
time.sleep(5)

text = """
public class Employee{
String name;
int id;
double salary;
public double AverageTax()
"""

pyautogui.write(text, interval=0.01)
