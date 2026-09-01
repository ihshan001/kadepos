#!/usr/bin/env python3
"""
Generates app/src/main/java/com/example/data/model/ProductCatalogPresets.kt

Rules enforced by this generator:
  * Every shop type has EXACTLY 50 products.
  * No product name is duplicated inside a shop type.
  * No product name is duplicated across shop types (zero overlap).
  * Every product is stamped with its shopType key so the app can filter
    strictly by the category chosen during onboarding.
"""

import os
import sys

OUT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app/src/main/java/com/example/data/model/ProductCatalogPresets.kt",
)

# (name, category, unit, sellingPrice, costPrice, favourite)
GROCERY = [
    ("Keeri Samba Rice 1kg", "Rice & Grains", "Kg", 285.0, 240.0, True),
    ("Nadu Rice 1kg", "Rice & Grains", "Kg", 220.0, 185.0, True),
    ("Red Raw Rice 1kg", "Rice & Grains", "Kg", 235.0, 198.0, False),
    ("Basmati Rice 1kg", "Rice & Grains", "Kg", 690.0, 580.0, False),
    ("Mysoor Dhal 1kg", "Rice & Grains", "Kg", 395.0, 330.0, True),
    ("Chickpeas (Kadala) 500g", "Rice & Grains", "Packet", 265.0, 215.0, False),
    ("Green Gram (Mung) 500g", "Rice & Grains", "Packet", 480.0, 405.0, False),
    ("White Sugar 1kg", "Cooking Essentials", "Kg", 265.0, 228.0, True),
    ("Table Salt 400g", "Cooking Essentials", "Packet", 65.0, 48.0, False),
    ("Coconut Oil 1L", "Cooking Essentials", "Bottle", 1150.0, 985.0, True),
    ("Sunflower Oil 1L", "Cooking Essentials", "Bottle", 920.0, 790.0, False),
    ("Chilli Powder 100g", "Cooking Essentials", "Packet", 175.0, 140.0, False),
    ("Turmeric Powder 100g", "Cooking Essentials", "Packet", 145.0, 112.0, False),
    ("Roasted Curry Powder 100g", "Cooking Essentials", "Packet", 190.0, 152.0, False),
    ("Wheat Flour 1kg", "Cooking Essentials", "Kg", 245.0, 205.0, True),
    ("Coconut Milk Powder 200g", "Cooking Essentials", "Packet", 385.0, 320.0, False),
    ("Full Cream Milk Powder 400g", "Dairy & Eggs", "Packet", 1180.0, 1030.0, True),
    ("Fresh Milk 1L", "Dairy & Eggs", "Bottle", 480.0, 405.0, True),
    ("Set Yoghurt Cup", "Dairy & Eggs", "Cup", 90.0, 68.0, True),
    ("Farm Eggs (10 Pack)", "Dairy & Eggs", "Pack", 620.0, 540.0, True),
    ("Cheddar Cheese 200g", "Dairy & Eggs", "Packet", 1250.0, 1060.0, False),
    ("Dairy Butter 227g", "Dairy & Eggs", "Packet", 1480.0, 1290.0, False),
    ("Cola Soft Drink 400ml", "Beverages", "Bottle", 190.0, 152.0, True),
    ("Lemon Lime Soda 400ml", "Beverages", "Bottle", 190.0, 152.0, True),
    ("Cream Soda 400ml", "Beverages", "Bottle", 190.0, 152.0, True),
    ("Mineral Water 1L", "Beverages", "Bottle", 120.0, 88.0, True),
    ("Ceylon Black Tea 200g", "Beverages", "Packet", 620.0, 520.0, True),
    ("Instant Coffee 50g", "Beverages", "Jar", 690.0, 585.0, False),
    ("Mango Nectar 1L", "Beverages", "Carton", 520.0, 430.0, False),
    ("Cream Cracker 190g", "Biscuits & Snacks", "Packet", 215.0, 172.0, True),
    ("Marie Biscuit 100g", "Biscuits & Snacks", "Packet", 105.0, 82.0, True),
    ("Chocolate Cream Biscuit 100g", "Biscuits & Snacks", "Packet", 135.0, 105.0, False),
    ("Gingerly Roll 100g", "Biscuits & Snacks", "Packet", 120.0, 92.0, False),
    ("Potato Chips 40g", "Biscuits & Snacks", "Packet", 190.0, 148.0, True),
    ("Murukku 100g", "Biscuits & Snacks", "Packet", 165.0, 128.0, False),
    ("Roasted Peanuts 100g", "Biscuits & Snacks", "Packet", 140.0, 108.0, False),
    ("Lemon Bath Soap 100g", "Personal Care", "Piece", 125.0, 96.0, True),
    ("Toothpaste 120g", "Personal Care", "Tube", 420.0, 348.0, True),
    ("Shampoo Bottle 180ml", "Personal Care", "Bottle", 545.0, 452.0, False),
    ("Twin Blade Razor (2 Pack)", "Personal Care", "Pack", 180.0, 138.0, False),
    ("Medium Toothbrush", "Personal Care", "Piece", 160.0, 118.0, False),
    ("Sanitary Napkins (8 Pack)", "Personal Care", "Pack", 460.0, 385.0, False),
    ("Laundry Detergent Powder 1kg", "Household", "Packet", 780.0, 655.0, True),
    ("Dishwash Liquid 500ml", "Household", "Bottle", 395.0, 322.0, False),
    ("Bleach Liquid 500ml", "Household", "Bottle", 285.0, 228.0, False),
    ("Garbage Bags (10 Pack)", "Household", "Pack", 320.0, 258.0, False),
    ("Mosquito Coil (10 Pack)", "Household", "Pack", 245.0, 195.0, False),
    ("Household Candles (6 Pack)", "Household", "Pack", 210.0, 165.0, False),
    ("Baby Diapers Medium (10 Pack)", "Baby Care", "Pack", 890.0, 760.0, False),
    ("Baby Wipes (80 Sheets)", "Baby Care", "Pack", 640.0, 535.0, False),
]

FOOD_CAFE = [
    ("Chicken Rice & Curry", "Rice & Curry", "Portion", 650.0, 380.0, True),
    ("Fish Rice & Curry", "Rice & Curry", "Portion", 550.0, 315.0, True),
    ("Vegetable Rice & Curry", "Rice & Curry", "Portion", 380.0, 190.0, True),
    ("Egg Rice & Curry", "Rice & Curry", "Portion", 450.0, 240.0, False),
    ("Chicken Biriyani", "Rice & Curry", "Portion", 1150.0, 660.0, True),
    ("Mutton Biriyani", "Rice & Curry", "Portion", 1650.0, 990.0, False),
    ("Chicken Kottu (Full)", "Kottu & Noodles", "Portion", 850.0, 470.0, True),
    ("Chicken Kottu (Half)", "Kottu & Noodles", "Portion", 500.0, 275.0, True),
    ("Cheese Kottu", "Kottu & Noodles", "Portion", 1050.0, 600.0, False),
    ("Vegetable Kottu", "Kottu & Noodles", "Portion", 520.0, 260.0, False),
    ("Egg Kottu", "Kottu & Noodles", "Portion", 620.0, 330.0, False),
    ("Chicken Fried Rice", "Kottu & Noodles", "Portion", 780.0, 430.0, True),
    ("Egg Fried Rice", "Kottu & Noodles", "Portion", 620.0, 330.0, True),
    ("Fish Bun", "Short Eats", "Piece", 100.0, 55.0, True),
    ("Vegetable Roti", "Short Eats", "Piece", 90.0, 45.0, True),
    ("Egg Roti", "Short Eats", "Piece", 130.0, 68.0, False),
    ("Chicken Roll", "Short Eats", "Piece", 150.0, 82.0, True),
    ("Fish Cutlet", "Short Eats", "Piece", 90.0, 48.0, True),
    ("Vegetable Patty", "Short Eats", "Piece", 110.0, 58.0, False),
    ("Chicken Samosa", "Short Eats", "Piece", 120.0, 62.0, False),
    ("Ulundu Wade", "Short Eats", "Piece", 70.0, 32.0, False),
    ("Isso Wade", "Short Eats", "Piece", 130.0, 70.0, False),
    ("Plain Hopper", "Hoppers & Pittu", "Piece", 45.0, 22.0, True),
    ("Egg Hopper", "Hoppers & Pittu", "Piece", 120.0, 62.0, True),
    ("Milk Hopper", "Hoppers & Pittu", "Piece", 140.0, 72.0, False),
    ("String Hoppers (10 Pieces)", "Hoppers & Pittu", "Plate", 220.0, 110.0, True),
    ("Pittu Portion", "Hoppers & Pittu", "Portion", 260.0, 130.0, False),
    ("Sandwich Bread Loaf", "Bakery", "Loaf", 240.0, 175.0, True),
    ("Butter Cake Slice", "Bakery", "Piece", 180.0, 90.0, True),
    ("Chocolate Cake Slice", "Bakery", "Piece", 260.0, 130.0, False),
    ("Cream Bun", "Bakery", "Piece", 110.0, 55.0, False),
    ("Jam Doughnut", "Bakery", "Piece", 130.0, 62.0, False),
    ("Sausage Bun", "Bakery", "Piece", 150.0, 78.0, False),
    ("Butter Croissant", "Bakery", "Piece", 280.0, 145.0, False),
    ("Plain Tea", "Hot Drinks", "Cup", 70.0, 22.0, True),
    ("Milk Tea", "Hot Drinks", "Cup", 130.0, 55.0, True),
    ("Ginger Tea", "Hot Drinks", "Cup", 90.0, 32.0, False),
    ("Black Coffee", "Hot Drinks", "Cup", 120.0, 42.0, False),
    ("Milk Coffee", "Hot Drinks", "Cup", 180.0, 78.0, True),
    ("Cappuccino", "Hot Drinks", "Cup", 450.0, 190.0, False),
    ("Iced Milo", "Cold Drinks", "Glass", 350.0, 165.0, True),
    ("Faluda", "Cold Drinks", "Glass", 420.0, 200.0, True),
    ("Fresh Lime Juice", "Cold Drinks", "Glass", 220.0, 85.0, True),
    ("Wood Apple Juice", "Cold Drinks", "Glass", 320.0, 140.0, False),
    ("Fresh Orange Juice", "Cold Drinks", "Glass", 420.0, 195.0, False),
    ("Iced Coffee", "Cold Drinks", "Glass", 480.0, 215.0, False),
    ("Watalappan", "Desserts", "Cup", 320.0, 150.0, True),
    ("Curd & Treacle", "Desserts", "Portion", 380.0, 185.0, False),
    ("Ice Cream Scoop", "Desserts", "Scoop", 250.0, 115.0, False),
    ("Chocolate Biscuit Pudding", "Desserts", "Portion", 420.0, 200.0, False),
]

PHARMACY = [
    ("Paracetamol 500mg (10 Tablets)", "Pain & Fever", "Card", 55.0, 32.0, True),
    ("Ibuprofen 400mg (10 Tablets)", "Pain & Fever", "Card", 120.0, 78.0, True),
    ("Diclofenac Gel 30g", "Pain & Fever", "Tube", 460.0, 340.0, False),
    ("Aspirin 75mg (10 Tablets)", "Pain & Fever", "Card", 65.0, 40.0, False),
    ("Mefenamic Acid 250mg (10 Tablets)", "Pain & Fever", "Card", 145.0, 95.0, False),
    ("Pain Relief Balm 10g", "Pain & Fever", "Jar", 280.0, 195.0, True),
    ("Cough Syrup 100ml", "Cold & Cough", "Bottle", 420.0, 305.0, True),
    ("Cetirizine 10mg (10 Tablets)", "Cold & Cough", "Card", 90.0, 55.0, True),
    ("Chlorpheniramine 4mg (10 Tablets)", "Cold & Cough", "Card", 60.0, 35.0, False),
    ("Nasal Decongestant Drops", "Cold & Cough", "Bottle", 320.0, 225.0, False),
    ("Menthol Inhaler Stick", "Cold & Cough", "Piece", 250.0, 165.0, False),
    ("Throat Lozenges (8 Pack)", "Cold & Cough", "Pack", 180.0, 120.0, False),
    ("Antacid Suspension 200ml", "Digestive Care", "Bottle", 380.0, 268.0, True),
    ("Omeprazole 20mg (10 Capsules)", "Digestive Care", "Card", 210.0, 145.0, False),
    ("ORS Rehydration Sachet", "Digestive Care", "Sachet", 60.0, 34.0, True),
    ("Domperidone 10mg (10 Tablets)", "Digestive Care", "Card", 130.0, 85.0, False),
    ("Activated Charcoal (10 Tablets)", "Digestive Care", "Card", 190.0, 130.0, False),
    ("Laxative Syrup 100ml", "Digestive Care", "Bottle", 460.0, 330.0, False),
    ("Amoxicillin 500mg (10 Capsules)", "Prescription", "Card", 420.0, 290.0, False),
    ("Metronidazole 400mg (10 Tablets)", "Prescription", "Card", 180.0, 118.0, False),
    ("Antibiotic Skin Cream 15g", "Prescription", "Tube", 340.0, 235.0, False),
    ("Sterile Eye Drops 10ml", "Prescription", "Bottle", 390.0, 270.0, False),
    ("Vitamin C 500mg (20 Tablets)", "Vitamins", "Bottle", 320.0, 215.0, True),
    ("Multivitamin Tablets (30s)", "Vitamins", "Bottle", 850.0, 620.0, True),
    ("Calcium + D3 (30 Tablets)", "Vitamins", "Bottle", 780.0, 570.0, False),
    ("Iron & Folic Acid (30 Tablets)", "Vitamins", "Bottle", 620.0, 445.0, False),
    ("Cod Liver Oil Capsules (30s)", "Vitamins", "Bottle", 980.0, 720.0, False),
    ("Zinc Tablets (20s)", "Vitamins", "Bottle", 420.0, 295.0, False),
    ("Adhesive Plasters (10 Pack)", "First Aid", "Pack", 190.0, 125.0, True),
    ("Cotton Wool 100g", "First Aid", "Roll", 260.0, 178.0, False),
    ("Gauze Bandage Roll", "First Aid", "Roll", 150.0, 98.0, False),
    ("Antiseptic Liquid 125ml", "First Aid", "Bottle", 520.0, 400.0, True),
    ("Antiseptic Wipes (10 Pack)", "First Aid", "Pack", 240.0, 165.0, False),
    ("Crepe Bandage 4 inch", "First Aid", "Roll", 380.0, 265.0, False),
    ("Micropore Tape 1 inch", "First Aid", "Roll", 210.0, 145.0, False),
    ("Digital Thermometer", "Devices", "Piece", 950.0, 640.0, True),
    ("Blood Pressure Monitor", "Devices", "Piece", 9500.0, 7200.0, False),
    ("Glucometer Test Strips (25s)", "Devices", "Box", 2450.0, 1900.0, False),
    ("Pulse Oximeter", "Devices", "Piece", 3200.0, 2350.0, False),
    ("Nebulizer Face Mask", "Devices", "Piece", 480.0, 320.0, False),
    ("Hot Water Bottle", "Devices", "Piece", 1250.0, 880.0, False),
    ("Surgical Face Mask (10 Pack)", "Wellness", "Pack", 160.0, 98.0, True),
    ("Hand Sanitizer 100ml", "Wellness", "Bottle", 260.0, 172.0, True),
    ("Sunscreen SPF50 50ml", "Wellness", "Tube", 1450.0, 1080.0, False),
    ("Moisturising Lotion 200ml", "Wellness", "Bottle", 980.0, 720.0, False),
    ("Baby Gripe Water 120ml", "Wellness", "Bottle", 340.0, 235.0, False),
    ("Diaper Rash Cream 50g", "Wellness", "Tube", 620.0, 450.0, False),
    ("Toothache Drops 10ml", "Wellness", "Bottle", 290.0, 195.0, False),
    ("Reading Glasses +2.00", "Wellness", "Piece", 850.0, 520.0, False),
    ("Weekly Pill Organiser Box", "Wellness", "Piece", 480.0, 310.0, False),
]

CLOTHING = [
    ("Cotton Crew T-Shirt", "Men's Wear", "Piece", 1850.0, 1080.0, True),
    ("Polo T-Shirt", "Men's Wear", "Piece", 2650.0, 1550.0, True),
    ("Formal Long Sleeve Shirt", "Men's Wear", "Piece", 3200.0, 1850.0, True),
    ("Casual Short Sleeve Shirt", "Men's Wear", "Piece", 2450.0, 1420.0, False),
    ("Slim Fit Denim Jeans", "Men's Wear", "Piece", 4500.0, 2700.0, True),
    ("Chino Trousers", "Men's Wear", "Piece", 3800.0, 2250.0, False),
    ("Formal Trousers", "Men's Wear", "Piece", 3950.0, 2350.0, False),
    ("Cargo Shorts", "Men's Wear", "Piece", 2200.0, 1280.0, False),
    ("Cotton Sarong", "Men's Wear", "Piece", 1450.0, 820.0, False),
    ("Batik Shirt", "Men's Wear", "Piece", 3600.0, 2050.0, False),
    ("Floral Summer Dress", "Women's Wear", "Piece", 3200.0, 1900.0, True),
    ("Maxi Dress", "Women's Wear", "Piece", 4200.0, 2500.0, False),
    ("Office Blouse", "Women's Wear", "Piece", 2650.0, 1520.0, True),
    ("Kurta Top", "Women's Wear", "Piece", 2950.0, 1700.0, False),
    ("Denim Skirt", "Women's Wear", "Piece", 3100.0, 1820.0, False),
    ("Palazzo Pants", "Women's Wear", "Piece", 2400.0, 1380.0, False),
    ("Stretch Leggings", "Women's Wear", "Piece", 1650.0, 920.0, True),
    ("Printed Saree", "Women's Wear", "Piece", 6500.0, 4100.0, False),
    ("Saree Jacket", "Women's Wear", "Piece", 1850.0, 1050.0, False),
    ("Cotton Night Dress", "Women's Wear", "Piece", 2100.0, 1180.0, False),
    ("Boys Cotton T-Shirt", "Kids Wear", "Piece", 1200.0, 680.0, True),
    ("Boys Casual Shorts", "Kids Wear", "Piece", 1100.0, 620.0, False),
    ("Girls Party Frock", "Kids Wear", "Piece", 2800.0, 1620.0, True),
    ("Girls Leggings", "Kids Wear", "Piece", 950.0, 520.0, False),
    ("School Uniform Shirt", "Kids Wear", "Piece", 1450.0, 880.0, True),
    ("School Uniform Shorts", "Kids Wear", "Piece", 1350.0, 810.0, False),
    ("Baby Romper", "Kids Wear", "Piece", 1250.0, 690.0, False),
    ("Kids Track Suit", "Kids Wear", "Set", 3200.0, 1900.0, False),
    ("Men's Brief (3 Pack)", "Innerwear", "Pack", 1450.0, 850.0, True),
    ("Men's Vest (2 Pack)", "Innerwear", "Pack", 1250.0, 720.0, False),
    ("Ladies Bra", "Innerwear", "Piece", 1650.0, 950.0, False),
    ("Ladies Panty (3 Pack)", "Innerwear", "Pack", 1350.0, 780.0, False),
    ("Thermal Top", "Innerwear", "Piece", 1950.0, 1150.0, False),
    ("Men's Boxer Shorts", "Innerwear", "Piece", 1150.0, 640.0, False),
    ("Rubber Slippers", "Footwear", "Pair", 750.0, 420.0, True),
    ("Leather Sandals", "Footwear", "Pair", 3800.0, 2300.0, False),
    ("Canvas Sneakers", "Footwear", "Pair", 4500.0, 2750.0, True),
    ("Formal Leather Shoes", "Footwear", "Pair", 6800.0, 4200.0, False),
    ("Ladies Heel Sandals", "Footwear", "Pair", 3900.0, 2350.0, False),
    ("Black School Shoes", "Footwear", "Pair", 3200.0, 1950.0, True),
    ("Leather Belt", "Accessories", "Piece", 1450.0, 780.0, True),
    ("Cotton Socks (3 Pack)", "Accessories", "Pack", 850.0, 470.0, True),
    ("Baseball Cap", "Accessories", "Piece", 1250.0, 680.0, False),
    ("Cotton Scarf", "Accessories", "Piece", 1150.0, 620.0, False),
    ("Ladies Handbag", "Accessories", "Piece", 4200.0, 2500.0, False),
    ("Leather Wallet", "Accessories", "Piece", 2200.0, 1250.0, False),
    ("Fashion Sunglasses", "Accessories", "Piece", 1850.0, 950.0, False),
    ("Hair Band Set", "Accessories", "Set", 450.0, 220.0, False),
    ("Formal Necktie", "Accessories", "Piece", 1350.0, 720.0, False),
    ("Folding Umbrella", "Accessories", "Piece", 1650.0, 950.0, False),
]

ELECTRONICS = [
    ("USB-C Charging Cable 1m", "Cables & Adapters", "Piece", 650.0, 290.0, True),
    ("Micro USB Cable 1m", "Cables & Adapters", "Piece", 480.0, 210.0, True),
    ("Lightning Cable 1m", "Cables & Adapters", "Piece", 950.0, 460.0, False),
    ("HDMI Cable 1.5m", "Cables & Adapters", "Piece", 1250.0, 680.0, False),
    ("AUX Audio Cable 1m", "Cables & Adapters", "Piece", 420.0, 180.0, False),
    ("USB Extension Cable 3m", "Cables & Adapters", "Piece", 780.0, 390.0, False),
    ("USB OTG Adapter", "Cables & Adapters", "Piece", 350.0, 150.0, False),
    ("LAN Network Cable 3m", "Cables & Adapters", "Piece", 690.0, 330.0, False),
    ("20W Fast Wall Charger", "Chargers & Power", "Piece", 1650.0, 880.0, True),
    ("33W Super Fast Charger", "Chargers & Power", "Piece", 2450.0, 1400.0, False),
    ("Dual Port Car Charger", "Chargers & Power", "Piece", 1150.0, 590.0, False),
    ("Power Bank 10000mAh", "Chargers & Power", "Piece", 4200.0, 2650.0, True),
    ("Power Bank 20000mAh", "Chargers & Power", "Piece", 6800.0, 4400.0, False),
    ("Wireless Charging Pad", "Chargers & Power", "Piece", 2650.0, 1500.0, False),
    ("Universal Travel Adapter", "Chargers & Power", "Piece", 1850.0, 1050.0, False),
    ("TWS Wireless Earbuds", "Audio", "Piece", 3500.0, 1950.0, True),
    ("Wired Earphones", "Audio", "Piece", 750.0, 340.0, True),
    ("Bluetooth Neckband", "Audio", "Piece", 2450.0, 1350.0, False),
    ("Small Bluetooth Speaker", "Audio", "Piece", 2950.0, 1700.0, False),
    ("Large Bluetooth Speaker", "Audio", "Piece", 7500.0, 4800.0, False),
    ("Over-Ear Headphones", "Audio", "Piece", 4500.0, 2650.0, False),
    ("Clip-on Microphone", "Audio", "Piece", 1250.0, 620.0, False),
    ("Tempered Glass Protector", "Phone Accessories", "Piece", 450.0, 150.0, True),
    ("Silicone Phone Case", "Phone Accessories", "Piece", 690.0, 280.0, True),
    ("Leather Flip Cover", "Phone Accessories", "Piece", 1350.0, 680.0, False),
    ("Popsocket Grip", "Phone Accessories", "Piece", 380.0, 145.0, False),
    ("Phone Ring Holder", "Phone Accessories", "Piece", 320.0, 120.0, False),
    ("Selfie Stick Tripod", "Phone Accessories", "Piece", 1850.0, 980.0, False),
    ("Car Phone Holder", "Phone Accessories", "Piece", 1150.0, 560.0, False),
    ("Phone Cleaning Kit", "Phone Accessories", "Set", 550.0, 240.0, False),
    ("Wireless Mouse", "Computer", "Piece", 1650.0, 900.0, True),
    ("Wired Keyboard", "Computer", "Piece", 2250.0, 1300.0, False),
    ("USB Flash Drive 32GB", "Computer", "Piece", 1850.0, 1150.0, True),
    ("USB Flash Drive 64GB", "Computer", "Piece", 2850.0, 1850.0, False),
    ("Laptop Cooling Pad", "Computer", "Piece", 3500.0, 2100.0, False),
    ("1080p Webcam", "Computer", "Piece", 4200.0, 2600.0, False),
    ("MicroSD Card 32GB", "Memory & Battery", "Piece", 1650.0, 1000.0, True),
    ("MicroSD Card 64GB", "Memory & Battery", "Piece", 2450.0, 1550.0, False),
    ("AA Alkaline Batteries (4 Pack)", "Memory & Battery", "Pack", 580.0, 380.0, True),
    ("AAA Alkaline Batteries (4 Pack)", "Memory & Battery", "Pack", 550.0, 360.0, True),
    ("Button Cell Battery CR2032", "Memory & Battery", "Piece", 180.0, 85.0, False),
    ("9W LED Bulb", "Lighting & Electrical", "Piece", 380.0, 250.0, True),
    ("12W LED Bulb", "Lighting & Electrical", "Piece", 520.0, 350.0, False),
    ("LED Tube Light 2ft", "Lighting & Electrical", "Piece", 950.0, 620.0, False),
    ("Rechargeable Torch", "Lighting & Electrical", "Piece", 1450.0, 850.0, False),
    ("LED Strip Light 5m", "Lighting & Electrical", "Roll", 2200.0, 1300.0, False),
    ("3-Way Extension Cord 2m", "Lighting & Electrical", "Piece", 1450.0, 920.0, True),
    ("Multi Plug Adapter", "Lighting & Electrical", "Piece", 680.0, 400.0, False),
    ("Wall Switch 1 Gang", "Lighting & Electrical", "Piece", 420.0, 250.0, False),
    ("Electrical Insulation Tape", "Lighting & Electrical", "Roll", 150.0, 80.0, False),
]

STATIONERY = [
    ("Blue Ballpoint Pen", "Writing", "Piece", 40.0, 22.0, True),
    ("Black Ballpoint Pen", "Writing", "Piece", 40.0, 22.0, True),
    ("Red Ballpoint Pen", "Writing", "Piece", 40.0, 22.0, False),
    ("Gel Pen 0.5mm", "Writing", "Piece", 120.0, 70.0, True),
    ("Fountain Pen", "Writing", "Piece", 450.0, 260.0, False),
    ("HB Pencils (10 Pack)", "Writing", "Pack", 320.0, 190.0, True),
    ("Mechanical Pencil", "Writing", "Piece", 180.0, 100.0, False),
    ("Pencil Lead Refill", "Writing", "Tube", 90.0, 48.0, False),
    ("Highlighter Pen", "Writing", "Piece", 160.0, 92.0, False),
    ("Permanent Marker", "Writing", "Piece", 210.0, 125.0, False),
    ("CR Book 80 Pages", "Books", "Book", 150.0, 98.0, True),
    ("CR Book 120 Pages", "Books", "Book", 220.0, 148.0, True),
    ("CR Book 200 Pages", "Books", "Book", 340.0, 235.0, True),
    ("Square Ruled Book 120 Pages", "Books", "Book", 240.0, 160.0, False),
    ("Drawing Book A4", "Books", "Book", 280.0, 185.0, False),
    ("Note Pad A5", "Books", "Book", 160.0, 100.0, False),
    ("Diary 2026", "Books", "Book", 850.0, 560.0, False),
    ("Attendance Register", "Books", "Book", 620.0, 420.0, False),
    ("Bill Book (50 Leaves)", "Books", "Book", 380.0, 245.0, False),
    ("Graph Book", "Books", "Book", 260.0, 172.0, False),
    ("A4 Copy Paper (500 Sheets)", "Paper", "Ream", 2450.0, 1950.0, True),
    ("A4 Copy Paper (100 Sheets)", "Paper", "Pack", 550.0, 420.0, True),
    ("Chart Paper", "Paper", "Sheet", 80.0, 45.0, False),
    ("Colour Paper Pack", "Paper", "Pack", 280.0, 175.0, False),
    ("Envelopes (10 Pack)", "Paper", "Pack", 180.0, 110.0, False),
    ("Brown Wrapping Paper", "Paper", "Sheet", 60.0, 32.0, False),
    ("Sticky Notes Pad", "Paper", "Pad", 220.0, 135.0, False),
    ("Small Stapler", "Office Supplies", "Piece", 520.0, 320.0, True),
    ("Staple Pins (Box)", "Office Supplies", "Box", 130.0, 78.0, False),
    ("Paper Clips (Box)", "Office Supplies", "Box", 150.0, 88.0, False),
    ("Binder Clips (Pack)", "Office Supplies", "Pack", 220.0, 135.0, False),
    ("Scissors 6 inch", "Office Supplies", "Piece", 380.0, 220.0, False),
    ("Glue Stick 15g", "Office Supplies", "Piece", 190.0, 115.0, True),
    ("White Glue 100ml", "Office Supplies", "Bottle", 240.0, 150.0, False),
    ("Cellophane Tape 1 inch", "Office Supplies", "Roll", 160.0, 95.0, False),
    ("Punching Machine", "Office Supplies", "Piece", 950.0, 620.0, False),
    ("File Folder", "Office Supplies", "Piece", 180.0, 105.0, True),
    ("Colour Pencils (12 Pack)", "Art & School", "Pack", 420.0, 265.0, True),
    ("Crayons (12 Pack)", "Art & School", "Pack", 320.0, 195.0, False),
    ("Water Colour Set", "Art & School", "Set", 520.0, 320.0, False),
    ("Poster Colour Set", "Art & School", "Set", 680.0, 430.0, False),
    ("Paint Brush Set", "Art & School", "Set", 380.0, 225.0, False),
    ("Geometry Box", "Art & School", "Set", 550.0, 340.0, True),
    ("Eraser & Sharpener Set", "Art & School", "Set", 120.0, 65.0, True),
    ("Ruler 30cm", "Art & School", "Piece", 90.0, 48.0, False),
    ("School Bag", "School Extras", "Piece", 3200.0, 2000.0, False),
    ("Water Bottle 750ml", "School Extras", "Piece", 950.0, 580.0, False),
    ("Lunch Box", "School Extras", "Piece", 1250.0, 780.0, False),
    ("Pencil Case", "School Extras", "Piece", 480.0, 280.0, False),
    ("Book Cover Roll", "School Extras", "Roll", 320.0, 195.0, False),
]

SALON = [
    ("Gents Haircut", "Hair Services", "Service", 800.0, 0.0, True),
    ("Ladies Haircut", "Hair Services", "Service", 1500.0, 0.0, True),
    ("Kids Haircut", "Hair Services", "Service", 600.0, 0.0, True),
    ("Beard Trim", "Hair Services", "Service", 400.0, 0.0, True),
    ("Clean Shave", "Hair Services", "Service", 350.0, 0.0, False),
    ("Hair Wash & Blow Dry", "Hair Services", "Service", 1200.0, 0.0, True),
    ("Hair Colouring (Short)", "Hair Services", "Service", 3500.0, 900.0, False),
    ("Hair Colouring (Long)", "Hair Services", "Service", 6500.0, 1600.0, False),
    ("Hair Straightening", "Hair Services", "Service", 9500.0, 2500.0, False),
    ("Keratin Treatment", "Hair Services", "Service", 12500.0, 3500.0, False),
    ("Hair Spa Treatment", "Hair Treatments", "Service", 3200.0, 800.0, False),
    ("Anti-Dandruff Treatment", "Hair Treatments", "Service", 2500.0, 650.0, False),
    ("Head Massage", "Hair Treatments", "Service", 1200.0, 0.0, True),
    ("Hair Oil Massage", "Hair Treatments", "Service", 1500.0, 250.0, False),
    ("Scalp Detox", "Hair Treatments", "Service", 2800.0, 700.0, False),
    ("Hair Trim Only", "Hair Treatments", "Service", 500.0, 0.0, False),
    ("Basic Facial", "Skin & Face", "Service", 2500.0, 600.0, True),
    ("Gold Facial", "Skin & Face", "Service", 4500.0, 1200.0, False),
    ("Fruit Facial", "Skin & Face", "Service", 3200.0, 850.0, False),
    ("Anti-Acne Facial", "Skin & Face", "Service", 3800.0, 1000.0, False),
    ("Face Clean Up", "Skin & Face", "Service", 1800.0, 400.0, True),
    ("Face Bleach", "Skin & Face", "Service", 1500.0, 380.0, False),
    ("Face Threading", "Skin & Face", "Service", 600.0, 0.0, True),
    ("Eyebrow Shaping", "Skin & Face", "Service", 500.0, 0.0, True),
    ("Full Arm Waxing", "Waxing", "Service", 1800.0, 450.0, False),
    ("Full Leg Waxing", "Waxing", "Service", 2500.0, 620.0, False),
    ("Underarm Waxing", "Waxing", "Service", 700.0, 180.0, True),
    ("Upper Lip Waxing", "Waxing", "Service", 400.0, 90.0, False),
    ("Full Body Waxing", "Waxing", "Service", 6500.0, 1600.0, False),
    ("Back Waxing", "Waxing", "Service", 1600.0, 400.0, False),
    ("Manicure", "Nails", "Service", 1500.0, 300.0, True),
    ("Pedicure", "Nails", "Service", 2000.0, 400.0, True),
    ("Gel Nail Polish", "Nails", "Service", 2500.0, 650.0, False),
    ("Nail Art (Per Hand)", "Nails", "Service", 1200.0, 250.0, False),
    ("Nail Extension", "Nails", "Service", 4500.0, 1200.0, False),
    ("Bridal Package", "Bridal & Makeup", "Service", 45000.0, 9000.0, False),
    ("Party Makeup", "Bridal & Makeup", "Service", 7500.0, 1500.0, False),
    ("Engagement Makeup", "Bridal & Makeup", "Service", 15000.0, 3000.0, False),
    ("Saree Draping", "Bridal & Makeup", "Service", 2500.0, 0.0, False),
    ("Occasion Hair Styling", "Bridal & Makeup", "Service", 3500.0, 500.0, False),
    ("Full Body Massage", "Massage & Body", "Service", 5500.0, 1000.0, False),
    ("Back & Shoulder Massage", "Massage & Body", "Service", 2500.0, 400.0, False),
    ("Foot Massage", "Massage & Body", "Service", 1800.0, 300.0, False),
    ("Body Scrub", "Massage & Body", "Service", 4200.0, 950.0, False),
    ("Hair Serum 100ml", "Salon Retail", "Bottle", 1850.0, 1100.0, False),
    ("Hair Gel 150g", "Salon Retail", "Jar", 950.0, 550.0, False),
    ("Salon Shampoo 250ml", "Salon Retail", "Bottle", 2200.0, 1350.0, False),
    ("Salon Conditioner 250ml", "Salon Retail", "Bottle", 2400.0, 1450.0, False),
    ("Face Wash 100ml", "Salon Retail", "Tube", 1250.0, 780.0, False),
    ("Hair Colour Box", "Salon Retail", "Box", 1450.0, 900.0, False),
]

REPAIR = [
    ("iPhone Screen Replacement", "Screen Repair", "Service", 28000.0, 19000.0, True),
    ("Samsung Screen Replacement", "Screen Repair", "Service", 18500.0, 12500.0, True),
    ("Android Screen Replacement", "Screen Repair", "Service", 9500.0, 6000.0, True),
    ("Tablet Screen Replacement", "Screen Repair", "Service", 14500.0, 9500.0, False),
    ("Touch Glass Replacement", "Screen Repair", "Service", 4500.0, 2500.0, False),
    ("LCD Panel Replacement", "Screen Repair", "Service", 12500.0, 8000.0, False),
    ("Screen Frame Repair", "Screen Repair", "Service", 3500.0, 1500.0, False),
    ("Laptop Screen Replacement", "Screen Repair", "Service", 24500.0, 17000.0, False),
    ("iPhone Battery Replacement", "Battery", "Service", 9500.0, 6200.0, True),
    ("Samsung Battery Replacement", "Battery", "Service", 6500.0, 4000.0, True),
    ("Android Battery Replacement", "Battery", "Service", 4500.0, 2600.0, True),
    ("Laptop Battery Replacement", "Battery", "Service", 12500.0, 8500.0, False),
    ("Tablet Battery Replacement", "Battery", "Service", 7500.0, 4800.0, False),
    ("Battery Health Check", "Battery", "Service", 500.0, 0.0, True),
    ("Charging Port Replacement", "Charging & Ports", "Service", 4500.0, 2200.0, True),
    ("USB-C Port Repair", "Charging & Ports", "Service", 5000.0, 2400.0, False),
    ("Headphone Jack Repair", "Charging & Ports", "Service", 3500.0, 1500.0, False),
    ("SIM Tray Replacement", "Charging & Ports", "Service", 1500.0, 600.0, False),
    ("Charging IC Repair", "Charging & Ports", "Service", 6500.0, 2800.0, False),
    ("Power Button Repair", "Charging & Ports", "Service", 3200.0, 1300.0, False),
    ("Software Flashing", "Software", "Service", 3500.0, 0.0, True),
    ("Factory Reset & Setup", "Software", "Service", 1500.0, 0.0, True),
    ("Data Backup", "Software", "Service", 2000.0, 0.0, False),
    ("Data Recovery", "Software", "Service", 8500.0, 1500.0, False),
    ("Virus Removal", "Software", "Service", 2500.0, 0.0, False),
    ("Laptop OS Installation", "Software", "Service", 4500.0, 0.0, False),
    ("Account / FRP Unlock", "Software", "Service", 5500.0, 500.0, False),
    ("Water Damage Cleaning", "Board Level", "Service", 7500.0, 1500.0, True),
    ("Motherboard Repair", "Board Level", "Service", 15000.0, 6000.0, False),
    ("Short Circuit Repair", "Board Level", "Service", 9500.0, 3500.0, False),
    ("Chip Level Repair", "Board Level", "Service", 18500.0, 8000.0, False),
    ("IC Reballing", "Board Level", "Service", 12500.0, 5000.0, False),
    ("Full Board Diagnosis", "Board Level", "Service", 1500.0, 0.0, True),
    ("Rear Camera Replacement", "Camera & Audio", "Service", 7500.0, 4200.0, False),
    ("Front Camera Replacement", "Camera & Audio", "Service", 5500.0, 3000.0, False),
    ("Loudspeaker Replacement", "Camera & Audio", "Service", 3500.0, 1600.0, False),
    ("Earpiece Replacement", "Camera & Audio", "Service", 3000.0, 1300.0, False),
    ("Microphone Replacement", "Camera & Audio", "Service", 3200.0, 1400.0, False),
    ("Vibrator Motor Replacement", "Camera & Audio", "Service", 2500.0, 1000.0, False),
    ("Back Glass Replacement", "Body & Housing", "Service", 6500.0, 3500.0, False),
    ("Full Housing Replacement", "Body & Housing", "Service", 8500.0, 4800.0, False),
    ("Button Flex Replacement", "Body & Housing", "Service", 3200.0, 1400.0, False),
    ("Antenna Repair", "Body & Housing", "Service", 4500.0, 1800.0, False),
    ("Device Deep Cleaning", "Body & Housing", "Service", 1500.0, 200.0, True),
    ("Fitted Tempered Glass", "Parts Counter", "Piece", 1200.0, 350.0, True),
    ("Phone Back Cover Case", "Parts Counter", "Piece", 950.0, 380.0, True),
    ("Replacement Charger Cable", "Parts Counter", "Piece", 850.0, 320.0, False),
    ("Original Charger Adapter", "Parts Counter", "Piece", 3500.0, 2200.0, False),
    ("Counter Stock Earphones", "Parts Counter", "Piece", 1100.0, 450.0, False),
    ("Screen Protector Film", "Parts Counter", "Piece", 450.0, 130.0, False),
]

FLOWERS = [
    ("Red Rose Stems (10)", "Fresh Flowers", "Bunch", 850.0, 620.0, True),
    ("White Rose Stems (10)", "Fresh Flowers", "Bunch", 900.0, 660.0, False),
    ("Pink Rose Stems (10)", "Fresh Flowers", "Bunch", 880.0, 645.0, False),
    ("Yellow Chrysanthemum Bunch", "Fresh Flowers", "Bunch", 650.0, 470.0, True),
    ("White Chrysanthemum Bunch", "Fresh Flowers", "Bunch", 650.0, 470.0, False),
    ("Orchid Stems (10)", "Fresh Flowers", "Bunch", 1200.0, 900.0, True),
    ("Gerbera Bunch (10)", "Fresh Flowers", "Bunch", 780.0, 560.0, False),
    ("Lily Stems (5)", "Fresh Flowers", "Bunch", 1100.0, 820.0, False),
    ("Carnation Bunch (10)", "Fresh Flowers", "Bunch", 720.0, 520.0, False),
    ("Tuberose Bunch", "Fresh Flowers", "Bunch", 420.0, 300.0, True),
    ("Jasmine String", "Fresh Flowers", "String", 350.0, 240.0, False),
    ("Marigold Garland", "Fresh Flowers", "String", 480.0, 340.0, True),
    ("Lotus Flowers (10)", "Fresh Flowers", "Bunch", 600.0, 430.0, True),
    ("Sunflower Bunch (5)", "Fresh Flowers", "Bunch", 950.0, 700.0, False),
    ("Mixed Seasonal Bunch", "Fresh Flowers", "Bunch", 700.0, 500.0, False),
    ("Anthurium Stems (5)", "Fresh Flowers", "Bunch", 1400.0, 1050.0, False),
    ("Baby Breath Bunch", "Fresh Flowers", "Bunch", 550.0, 390.0, False),
    ("Fern Leaves Bunch", "Fresh Flowers", "Bunch", 250.0, 170.0, False),
    ("Birthday Bouquet Small", "Bouquets", "Bouquet", 2500.0, 1650.0, True),
    ("Birthday Bouquet Large", "Bouquets", "Bouquet", 4500.0, 3000.0, False),
    ("Anniversary Bouquet", "Bouquets", "Bouquet", 3800.0, 2500.0, True),
    ("Thank You Bouquet", "Bouquets", "Bouquet", 2800.0, 1850.0, False),
    ("Get Well Bouquet", "Bouquets", "Bouquet", 2700.0, 1780.0, False),
    ("Congratulations Bouquet", "Bouquets", "Bouquet", 3200.0, 2100.0, False),
    ("Sympathy Wreath", "Bouquets", "Piece", 5500.0, 3600.0, False),
    ("Bridal Bouquet", "Wedding", "Bouquet", 8500.0, 5600.0, True),
    ("Bridesmaid Bouquet", "Wedding", "Bouquet", 4200.0, 2780.0, False),
    ("Groom Boutonniere", "Wedding", "Piece", 850.0, 550.0, False),
    ("Wedding Car Garland", "Wedding", "Piece", 6500.0, 4300.0, False),
    ("Wedding Stage Flowers", "Wedding", "Set", 25000.0, 16500.0, False),
    ("Floral Table Centre", "Wedding", "Piece", 3800.0, 2500.0, False),
    ("Money Plant in Pot", "Plants", "Pot", 1200.0, 780.0, True),
    ("Areca Palm Plant", "Plants", "Pot", 3500.0, 2300.0, False),
    ("Peace Lily Plant", "Plants", "Pot", 2800.0, 1850.0, True),
    ("Snake Plant", "Plants", "Pot", 2400.0, 1580.0, False),
    ("Orchid Plant in Pot", "Plants", "Pot", 4200.0, 2780.0, False),
    ("Succulent Small Pot", "Plants", "Pot", 850.0, 520.0, True),
    ("Terracotta Pot Medium", "Plants", "Pot", 480.0, 300.0, False),
    ("Ceramic Pot Large", "Plants", "Pot", 1650.0, 1080.0, False),
    ("Glass Vase Small", "Vases & Decor", "Piece", 750.0, 480.0, True),
    ("Glass Vase Tall", "Vases & Decor", "Piece", 1850.0, 1220.0, False),
    ("Ceramic Vase Decorative", "Vases & Decor", "Piece", 2400.0, 1580.0, False),
    ("Flower Foam Block", "Vases & Decor", "Piece", 380.0, 240.0, False),
    ("Gift Wrap Sheet Large", "Gift Wrap", "Sheet", 180.0, 110.0, True),
    ("Gift Bag Medium", "Gift Wrap", "Piece", 250.0, 155.0, True),
    ("Gift Box Small", "Gift Wrap", "Piece", 320.0, 200.0, False),
    ("Satin Ribbon Roll", "Ribbons & Accessories", "Roll", 450.0, 280.0, True),
    ("Greeting Card Birthday", "Greeting Cards", "Card", 220.0, 130.0, True),
    ("Greeting Card Anniversary", "Greeting Cards", "Card", 220.0, 130.0, False),
    ("Greeting Card Blank", "Greeting Cards", "Card", 180.0, 105.0, False),
]

SHOES = [
    ("Mens Oxford Leather Shoe", "Mens Shoes", "Pair", 8900.0, 6200.0, True),
    ("Mens Loafer Casual", "Mens Shoes", "Pair", 6500.0, 4500.0, True),
    ("Mens Derby Shoe", "Mens Shoes", "Pair", 7800.0, 5400.0, False),
    ("Mens Moccasin Shoe", "Mens Shoes", "Pair", 5900.0, 4100.0, False),
    ("Mens Leather Boot", "Mens Shoes", "Pair", 12500.0, 8700.0, False),
    ("Mens Office Slip-On", "Mens Shoes", "Pair", 5400.0, 3750.0, False),
    ("Ladies Flat Shoe", "Ladies Shoes", "Pair", 4200.0, 2900.0, True),
    ("Ladies Wedge Sandal", "Ladies Shoes", "Pair", 5600.0, 3900.0, True),
    ("Ladies Block Heel", "Ladies Shoes", "Pair", 6800.0, 4700.0, False),
    ("Ladies Stiletto Pump", "Ladies Shoes", "Pair", 7500.0, 5200.0, False),
    ("Ladies Ballet Flat", "Ladies Shoes", "Pair", 3900.0, 2700.0, False),
    ("Ladies Ankle Boot", "Ladies Shoes", "Pair", 9800.0, 6800.0, False),
    ("Kids Velcro Sandal", "Kids Shoes", "Pair", 2800.0, 1900.0, True),
    ("Kids School Shoe Black", "Kids Shoes", "Pair", 3600.0, 2480.0, True),
    ("Kids Canvas Shoe", "Kids Shoes", "Pair", 2400.0, 1650.0, False),
    ("Kids Sports Shoe", "Kids Shoes", "Pair", 4200.0, 2900.0, False),
    ("Kids Rain Boot", "Kids Shoes", "Pair", 3200.0, 2180.0, False),
    ("Toddler Soft Sandal", "Kids Shoes", "Pair", 1900.0, 1280.0, False),
    ("Running Shoe Mens", "Sports Footwear", "Pair", 11500.0, 8000.0, True),
    ("Running Shoe Ladies", "Sports Footwear", "Pair", 10800.0, 7500.0, False),
    ("Training Shoe Unisex", "Sports Footwear", "Pair", 8900.0, 6200.0, True),
    ("Cricket Spike Shoe", "Sports Footwear", "Pair", 9600.0, 6700.0, False),
    ("Football Cleats Firm Ground", "Sports Footwear", "Pair", 7400.0, 5100.0, False),
    ("Badminton Court Shoe", "Sports Footwear", "Pair", 8200.0, 5700.0, False),
    ("Hiking Shoe", "Sports Footwear", "Pair", 13500.0, 9400.0, False),
    ("Mens Leather Sandal", "Sandals", "Pair", 4800.0, 3300.0, True),
    ("Mens Beach Slipper", "Sandals", "Pair", 1800.0, 1200.0, True),
    ("Ladies Flat Sandal", "Sandals", "Pair", 3200.0, 2180.0, False),
    ("Ladies Wedge Slipper", "Sandals", "Pair", 2900.0, 1980.0, False),
    ("House Slipper Pair", "Sandals", "Pair", 1600.0, 1050.0, True),
    ("Shoe Polish Black", "Shoe Care", "Piece", 450.0, 290.0, True),
    ("Shoe Polish Brown", "Shoe Care", "Piece", 450.0, 290.0, False),
    ("Shoe Brush Wooden", "Shoe Care", "Piece", 650.0, 420.0, False),
    ("Shoe Shine Sponge", "Shoe Care", "Piece", 350.0, 220.0, False),
    ("Leather Conditioner", "Shoe Care", "Bottle", 1250.0, 820.0, False),
    ("Shoe Deodorant Spray", "Shoe Care", "Bottle", 980.0, 640.0, False),
    ("Waterproof Shoe Spray", "Shoe Care", "Bottle", 1450.0, 950.0, False),
    ("Shoe Horn Metal", "Shoe Care", "Piece", 750.0, 480.0, False),
    ("Cotton Socks Mens (3 Pack)", "Socks", "Pack", 890.0, 580.0, True),
    ("Ankle Socks Ladies (3 Pack)", "Socks", "Pack", 780.0, 510.0, False),
    ("School Socks Kids (3 Pack)", "Socks", "Pack", 720.0, 470.0, True),
    ("Sports Socks Cushioned", "Socks", "Pair", 550.0, 360.0, False),
    ("Shoe Laces Pair", "Accessories", "Pair", 250.0, 155.0, True),
    ("Orthopaedic Insole", "Accessories", "Pair", 1450.0, 950.0, False),
    ("Shoe Tree Cedar", "Accessories", "Pair", 2800.0, 1850.0, False),
    ("Shoe Storage Box", "Accessories", "Piece", 1250.0, 820.0, False),
    ("Heel Tip Replacement", "Accessories", "Pair", 650.0, 420.0, False),
    ("Anti-Slip Sole Pad", "Accessories", "Pair", 480.0, 310.0, False),
    ("Shoe Bag Travel", "Accessories", "Piece", 1650.0, 1080.0, False),
    ("Collapsible Shoe Rack", "Accessories", "Piece", 4500.0, 2980.0, False),
]

HARDWARE = [
    ("Claw Hammer 16oz", "Hand Tools", "Piece", 1850.0, 1220.0, True),
    ("Screwdriver Set (6 Piece)", "Hand Tools", "Set", 2400.0, 1580.0, True),
    ("Adjustable Spanner 10 inch", "Hand Tools", "Piece", 1450.0, 950.0, True),
    ("Combination Pliers 8 inch", "Hand Tools", "Piece", 1250.0, 820.0, True),
    ("Measuring Tape 5m", "Hand Tools", "Piece", 780.0, 510.0, True),
    ("Utility Knife", "Hand Tools", "Piece", 450.0, 290.0, False),
    ("Hacksaw Frame", "Hand Tools", "Piece", 1250.0, 820.0, False),
    ("Spirit Level 60cm", "Hand Tools", "Piece", 2200.0, 1450.0, False),
    ("Putty Knife", "Hand Tools", "Piece", 420.0, 270.0, False),
    ("Steel File 10 inch", "Hand Tools", "Piece", 650.0, 420.0, False),
    ("Wood Screws 1 inch (100)", "Fasteners", "Packet", 450.0, 290.0, True),
    ("Wood Screws 2 inch (100)", "Fasteners", "Packet", 680.0, 440.0, False),
    ("Machine Bolts M8 (20)", "Fasteners", "Packet", 720.0, 470.0, False),
    ("Steel Nails 2 inch (500g)", "Fasteners", "Packet", 380.0, 240.0, True),
    ("Concrete Nails (250g)", "Fasteners", "Packet", 420.0, 270.0, False),
    ("Wall Plug Pack (100)", "Fasteners", "Packet", 350.0, 220.0, True),
    ("Cable Clips (50)", "Fasteners", "Packet", 280.0, 180.0, False),
    ("Nuts and Washers Set", "Fasteners", "Set", 950.0, 620.0, False),
    ("Emulsion Paint White 1L", "Paint", "Tin", 2450.0, 1620.0, True),
    ("Emulsion Paint White 4L", "Paint", "Tin", 8200.0, 5400.0, True),
    ("Exterior Paint 4L", "Paint", "Tin", 9800.0, 6450.0, False),
    ("Enamel Paint 1L", "Paint", "Tin", 2850.0, 1880.0, False),
    ("Primer Sealer 1L", "Paint", "Tin", 2200.0, 1450.0, False),
    ("Paint Thinner 1L", "Paint", "Bottle", 780.0, 510.0, True),
    ("Paint Roller 9 inch", "Paint", "Piece", 650.0, 420.0, True),
    ("Paint Brush 3 inch", "Paint", "Piece", 420.0, 270.0, True),
    ("Masking Tape Roll", "Paint", "Roll", 350.0, 220.0, False),
    ("Sandpaper Sheet", "Paint", "Sheet", 120.0, 75.0, False),
    ("Electrical Wire 1.5mm (10m)", "Electrical", "Roll", 3200.0, 2100.0, True),
    ("Switch Socket 13A", "Electrical", "Piece", 650.0, 420.0, True),
    ("PVC Conduit 20mm (3m)", "Electrical", "Piece", 480.0, 310.0, False),
    ("Insulation Tape Roll", "Electrical", "Roll", 220.0, 140.0, True),
    ("PVC Pipe Half Inch (3m)", "Plumbing", "Piece", 780.0, 510.0, True),
    ("PVC Elbow Half Inch", "Plumbing", "Piece", 95.0, 60.0, True),
    ("Tap Washer Set", "Plumbing", "Packet", 180.0, 115.0, False),
    ("PTFE Tape Roll", "Plumbing", "Roll", 150.0, 95.0, True),
    ("Water Tap Chrome", "Plumbing", "Piece", 1850.0, 1220.0, False),
    ("Flexible Hose 1.5m", "Plumbing", "Piece", 1250.0, 820.0, False),
    ("Cement Bag 50kg", "Building", "Bag", 1650.0, 1420.0, True),
    ("River Sand Load", "Building", "Load", 18500.0, 15000.0, False),
    ("Cement Block Standard", "Building", "Piece", 145.0, 98.0, True),
    ("Roofing Sheet 8ft", "Building", "Sheet", 4800.0, 3150.0, False),
    ("Steel Rod 12mm (6m)", "Building", "Piece", 4200.0, 3480.0, False),
    ("Gravel Load", "Building", "Load", 22000.0, 18000.0, False),
    ("Safety Helmet Yellow", "Safety", "Piece", 1650.0, 1080.0, True),
    ("Work Gloves Pair", "Safety", "Pair", 650.0, 420.0, True),
    ("Safety Goggles", "Safety", "Piece", 850.0, 560.0, False),
    ("Dust Mask (5 Pack)", "Safety", "Pack", 550.0, 360.0, False),
    ("PVC Glue 100ml", "Adhesives", "Bottle", 450.0, 290.0, True),
    ("Contact Adhesive 250ml", "Adhesives", "Bottle", 780.0, 510.0, False),
]

TOYS_GIFTS = [
    ("Teddy Bear Small", "Soft Toys", "Piece", 1850.0, 1180.0, True),
    ("Teddy Bear Large", "Soft Toys", "Piece", 4200.0, 2700.0, True),
    ("Plush Elephant", "Soft Toys", "Piece", 2400.0, 1550.0, False),
    ("Plush Rabbit", "Soft Toys", "Piece", 1950.0, 1250.0, False),
    ("Soft Dress-Up Doll", "Soft Toys", "Piece", 2800.0, 1800.0, False),
    ("Building Blocks (100 Piece)", "Educational", "Set", 2450.0, 1580.0, True),
    ("Wooden Puzzle 24 Piece", "Educational", "Set", 1450.0, 940.0, True),
    ("Alphabet Flash Cards", "Educational", "Pack", 750.0, 480.0, False),
    ("Numbers Counting Beads", "Educational", "Set", 1250.0, 810.0, False),
    ("Kids Drawing Board", "Educational", "Piece", 2200.0, 1430.0, False),
    ("Colour Pencils (24 Pack)", "Educational", "Pack", 850.0, 550.0, True),
    ("Kids Sketch Book A4", "Educational", "Book", 550.0, 355.0, False),
    ("Junior Science Kit", "Educational", "Set", 3800.0, 2450.0, False),
    ("Remote Control Car", "Outdoor Play", "Piece", 6500.0, 4200.0, True),
    ("Frisbee", "Outdoor Play", "Piece", 850.0, 550.0, False),
    ("Kids Skipping Rope", "Outdoor Play", "Piece", 450.0, 290.0, True),
    ("Junior Football Size 3", "Outdoor Play", "Piece", 2200.0, 1430.0, True),
    ("Beach Ball Inflatable", "Outdoor Play", "Piece", 650.0, 420.0, False),
    ("Water Gun Large", "Outdoor Play", "Piece", 1250.0, 810.0, False),
    ("Kite with String", "Outdoor Play", "Piece", 750.0, 480.0, False),
    ("Bubble Blower Bottle", "Outdoor Play", "Bottle", 350.0, 225.0, True),
    ("Family Board Game", "Games & Puzzles", "Set", 3450.0, 2230.0, True),
    ("Chess Set Wooden", "Games & Puzzles", "Set", 2800.0, 1810.0, False),
    ("Playing Cards Deck", "Games & Puzzles", "Pack", 350.0, 225.0, True),
    ("Jigsaw Puzzle 500 Piece", "Games & Puzzles", "Set", 1950.0, 1260.0, False),
    ("Domino Set", "Games & Puzzles", "Set", 1250.0, 810.0, False),
    ("Snake and Ladder Board", "Games & Puzzles", "Set", 1450.0, 940.0, False),
    ("Balloon Pack (20)", "Party Supplies", "Pack", 450.0, 290.0, True),
    ("Birthday Banner Set", "Party Supplies", "Set", 850.0, 550.0, True),
    ("Party Hats (6 Pack)", "Party Supplies", "Pack", 650.0, 420.0, False),
    ("Gift Ribbon Bow", "Party Supplies", "Piece", 250.0, 160.0, False),
    ("Return Gift Bag (10)", "Party Supplies", "Pack", 750.0, 480.0, False),
    ("Confetti Popper", "Party Supplies", "Piece", 550.0, 355.0, False),
    ("Fancy Keychain", "Fancy Items", "Piece", 350.0, 225.0, True),
    ("Photo Frame 6x4", "Fancy Items", "Piece", 850.0, 550.0, True),
    ("Scented Candle Jar", "Fancy Items", "Jar", 1450.0, 940.0, True),
    ("Decorative Wind Chime", "Fancy Items", "Piece", 1950.0, 1260.0, False),
    ("Designer Wall Clock", "Fancy Items", "Piece", 3450.0, 2230.0, False),
    ("Showpiece Figurine", "Fancy Items", "Piece", 1650.0, 1070.0, False),
    ("Friendship Greeting Card", "Fancy Items", "Card", 220.0, 140.0, False),
    ("Craft Glue 100ml", "Craft", "Bottle", 450.0, 290.0, True),
    ("Glitter Pack Assorted", "Craft", "Pack", 550.0, 355.0, False),
    ("Origami Paper Pack", "Craft", "Pack", 650.0, 420.0, False),
    ("Kids Safety Scissors", "Craft", "Piece", 350.0, 225.0, False),
    ("Water Colour Set (12)", "Craft", "Set", 850.0, 550.0, False),
    ("Baby Rattle Set", "Baby Toys", "Set", 950.0, 610.0, True),
    ("Teething Ring", "Baby Toys", "Piece", 550.0, 355.0, False),
    ("Soft Activity Cube", "Baby Toys", "Piece", 1850.0, 1190.0, False),
    ("Musical Cot Mobile", "Baby Toys", "Piece", 3200.0, 2070.0, False),
    ("Stacking Rings Toy", "Baby Toys", "Set", 1250.0, 810.0, False),
]

SPORTS = [
    ("English Willow Cricket Bat", "Cricket", "Piece", 18500.0, 12200.0, True),
    ("Kashmir Willow Cricket Bat", "Cricket", "Piece", 8500.0, 5600.0, True),
    ("Leather Cricket Ball Red", "Cricket", "Piece", 2850.0, 1880.0, True),
    ("Tennis Ball Cricket", "Cricket", "Piece", 450.0, 290.0, True),
    ("Cricket Batting Pads", "Cricket", "Pair", 6500.0, 4280.0, False),
    ("Cricket Batting Gloves", "Cricket", "Pair", 4800.0, 3160.0, False),
    ("Wicket Keeping Gloves", "Cricket", "Pair", 7200.0, 4740.0, False),
    ("Cricket Helmet", "Cricket", "Piece", 8500.0, 5600.0, False),
    ("Cricket Stumps Set", "Cricket", "Set", 5500.0, 3620.0, False),
    ("Cricket Kit Bag", "Cricket", "Piece", 6800.0, 4480.0, False),
    ("Football Size 5", "Football", "Piece", 3450.0, 2270.0, True),
    ("Football Goal Net", "Football", "Set", 12500.0, 8240.0, False),
    ("Football Shin Guards", "Football", "Pair", 1950.0, 1280.0, False),
    ("Training Cones (10)", "Football", "Set", 1450.0, 950.0, False),
    ("Referee Whistle", "Football", "Piece", 450.0, 290.0, False),
    ("Badminton Rackets (2)", "Badminton", "Set", 4800.0, 3160.0, True),
    ("Badminton Racket Grip", "Badminton", "Piece", 350.0, 225.0, True),
    ("Shuttlecocks (6 Pack)", "Badminton", "Pack", 1250.0, 810.0, True),
    ("Badminton Net Set", "Badminton", "Set", 6800.0, 4480.0, False),
    ("Squash Racket", "Badminton", "Piece", 9500.0, 6260.0, False),
    ("Table Tennis Bat", "Indoor Games", "Piece", 1850.0, 1220.0, True),
    ("Table Tennis Balls (6)", "Indoor Games", "Pack", 550.0, 355.0, True),
    ("Carrom Board Set", "Indoor Games", "Set", 8500.0, 5600.0, False),
    ("Dart Board Set", "Indoor Games", "Set", 4200.0, 2770.0, False),
    ("Yoga Mat 6mm", "Fitness", "Piece", 3200.0, 2100.0, True),
    ("Dumbbell Pair 5kg", "Fitness", "Pair", 6500.0, 4280.0, True),
    ("Adjustable Dumbbell Set", "Fitness", "Set", 14500.0, 9560.0, False),
    ("Resistance Band Set", "Fitness", "Set", 2450.0, 1610.0, True),
    ("Skipping Rope Speed", "Fitness", "Piece", 850.0, 550.0, True),
    ("Exercise Ball 65cm", "Fitness", "Piece", 4200.0, 2770.0, False),
    ("Pull Up Bar Doorway", "Fitness", "Piece", 3800.0, 2500.0, False),
    ("Swimming Goggles", "Swimming", "Piece", 2450.0, 1610.0, True),
    ("Swimming Cap Silicone", "Swimming", "Piece", 1450.0, 950.0, False),
    ("Kids Arm Float Bands", "Swimming", "Pair", 1250.0, 810.0, False),
    ("Swim Trunks Mens", "Swimming", "Piece", 2850.0, 1880.0, False),
    ("Bicycle Helmet", "Cycling", "Piece", 4850.0, 3200.0, True),
    ("Bicycle Tube 26 inch", "Cycling", "Piece", 1250.0, 810.0, True),
    ("Bicycle Pump", "Cycling", "Piece", 1850.0, 1220.0, False),
    ("Bicycle Chain Oil", "Cycling", "Bottle", 750.0, 480.0, False),
    ("Bicycle Rear Light", "Cycling", "Piece", 1450.0, 950.0, False),
    ("Camping Tent 2 Person", "Outdoor", "Piece", 12500.0, 8240.0, False),
    ("Insulated Water Bottle 1L", "Outdoor", "Bottle", 2450.0, 1610.0, True),
    ("Hiking Backpack 40L", "Outdoor", "Piece", 9500.0, 6260.0, False),
    ("Sports T-Shirt Dry Fit", "Sportswear", "Piece", 2450.0, 1610.0, True),
    ("Sports Shorts Unisex", "Sportswear", "Piece", 1950.0, 1280.0, True),
    ("Track Suit Set", "Sportswear", "Set", 6500.0, 4280.0, False),
    ("Sports Towel Microfibre", "Sportswear", "Piece", 1250.0, 810.0, False),
    ("Sports Cap Adjustable", "Sportswear", "Piece", 1450.0, 950.0, False),
    ("Gym Gloves Pair", "Sportswear", "Pair", 1850.0, 1220.0, False),
    ("Sports Water Belt", "Sportswear", "Piece", 2200.0, 1450.0, False),
]

SHOPS = [
    # key, display, tagline, iconName, fn, tracksStock, businessType, barcodePrefix
    ("GROCERY", "Grocery & Supermarket", "Rice, sugar, tea, milk, biscuits, soap and daily essentials",
     "shopping_basket", "groceryProducts", True, "Retail", "20"),
    ("FOOD_CAFE", "Restaurant, Cafe & Bakery", "Rice & curry, kottu, short eats, tea, cakes and juices",
     "restaurant", "foodCafeProducts", False, "Food", "21"),
    ("PHARMACY", "Pharmacy & Healthcare", "Medicines, vitamins, first aid, devices and wellness items",
     "local_pharmacy", "pharmacyProducts", True, "Retail", "22"),
    ("CLOTHING", "Clothing & Fashion", "Shirts, dresses, kids wear, footwear and accessories",
     "checkroom", "clothingProducts", True, "Retail", "23"),
    ("ELECTRONICS", "Electronics & Mobile", "Cables, chargers, earbuds, memory cards and LED bulbs",
     "devices", "electronicsProducts", True, "Retail", "24"),
    ("STATIONERY", "Bookshop & Stationery", "Pens, books, paper, office supplies and school items",
     "menu_book", "stationeryProducts", True, "Retail", "25"),
    ("SALON", "Salon, Spa & Beauty", "Haircuts, facials, waxing, nails, bridal and massage",
     "content_cut", "salonProducts", False, "Service", "26"),
    ("REPAIR", "Mobile & Device Repair", "Screens, batteries, ports, software fixes and spare parts",
     "build", "repairProducts", False, "Repair", "27"),
    ("FLOWERS", "Flower & Gift Shop", "Fresh flowers, bouquets, plants, vases and gift wrapping",
     "local_florist", "flowerProducts", True, "Retail", "28"),
    ("SHOES", "Shoe & Footwear Store", "Mens, ladies and kids shoes, sandals, socks and shoe care",
     "do_not_step", "shoesProducts", True, "Retail", "29"),
    ("HARDWARE", "Hardware & Building Supplies", "Tools, fasteners, paint, plumbing, wiring and building materials",
     "hardware", "hardwareProducts", True, "Retail", "30"),
    ("TOYS_GIFTS", "Toys, Gifts & Fancy Goods", "Toys, puzzles, party supplies, craft and fancy home gifts",
     "toys", "toysGiftsProducts", True, "Retail", "31"),
    ("SPORTS", "Sports & Fitness", "Cricket, football, badminton, fitness, swimming and cycling gear",
     "sports_soccer", "sportsProducts", True, "Retail", "32"),
]

DATA = {
    "GROCERY": GROCERY,
    "FOOD_CAFE": FOOD_CAFE,
    "PHARMACY": PHARMACY,
    "CLOTHING": CLOTHING,
    "ELECTRONICS": ELECTRONICS,
    "STATIONERY": STATIONERY,
    "SALON": SALON,
    "REPAIR": REPAIR,
    "FLOWERS": FLOWERS,
    "SHOES": SHOES,
    "HARDWARE": HARDWARE,
    "TOYS_GIFTS": TOYS_GIFTS,
    "SPORTS": SPORTS,
}


def sku_for(prefix_word, index):
    return "%s%03d" % (prefix_word, index)


def validate():
    seen = {}
    errors = []
    for key, items in DATA.items():
        if len(items) != 50:
            errors.append("%s has %d products (expected 50)" % (key, len(items)))
        local = set()
        for name, *_ in items:
            n = name.strip().lower()
            if n in local:
                errors.append("duplicate inside %s: %s" % (key, name))
            local.add(n)
            if n in seen:
                errors.append("duplicate across shops: '%s' in %s and %s" % (name, seen[n], key))
            else:
                seen[n] = key
    if errors:
        for e in errors:
            print("ERROR:", e)
        sys.exit(1)
    print("Validated %d shop types, %d unique products total." % (len(DATA), len(seen)))


def kt_escape(s):
    return s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")


def render_products(key, items, sku_prefix, barcode_prefix, tracks_stock):
    lines = []
    for i, (name, category, unit, sell, cost, fav) in enumerate(items, start=1):
        sku = "%s-%03d" % (sku_prefix, i)
        barcode = "%s%04d%04d" % (barcode_prefix, (i * 37) % 10000, (int(sell) * 7) % 10000)
        is_service = unit == "Service"
        stock = 0.0 if not tracks_stock or is_service else float(24 + (i * 3) % 60)
        low = 0.0 if not tracks_stock or is_service else float(4 + (i % 5))
        lines.append(
            '        P("%s", %s, %s, "%s", "%s", "%s", "%s", %s, %s, %s, %s),'
            % (
                kt_escape(name),
                sell,
                cost,
                kt_escape(category),
                unit,
                sku,
                barcode if not is_service else "",
                stock,
                low,
                "true" if fav else "false",
                "false" if is_service else "true",
            )
        )
    return "\n".join(lines)


HEADER = '''package com.example.data.model

/**
 * Starter catalogues for each shop type offered during setup.
 *
 * GENERATED FILE - edit tools/generate_catalog.py and re-run it instead of
 * hand editing, so the "no duplicates / exactly 50 per shop type" guarantees
 * keep holding.
 *
 * Guarantees enforced by the generator:
 *   - every shop type has exactly 50 products
 *   - no product name repeats inside a shop type
 *   - no product name is shared between two shop types
 *   - every product carries its shopType, so screens can filter strictly
 */
object ProductCatalogPresets {

    /** Compact tuple used only while building the preset lists. */
    private data class P(
        val name: String,
        val sellingPrice: Double,
        val costPrice: Double,
        val category: String,
        val unit: String,
        val sku: String,
        val barcode: String,
        val stock: Double,
        val lowStock: Double,
        val favourite: Boolean,
        val tracked: Boolean
    )

    data class ShopTypePreset(
        val key: String,
        val displayName: String,
        val description: String,
        val iconName: String,
        val businessType: String,
        val tracksStockByDefault: Boolean,
        val products: List<ProductEntity>
    ) {
        /** Distinct product categories inside this shop type, in display order. */
        val categories: List<String> get() = products.map { it.category }.distinct()
    }

    private fun P.toEntity(shopType: String, tracksStock: Boolean) = ProductEntity(
        name = name,
        sellingPrice = sellingPrice,
        costPrice = costPrice,
        barcode = barcode,
        sku = sku,
        category = category,
        unit = unit,
        shopType = shopType,
        currentStock = if (tracksStock && tracked) stock else 0.0,
        lowStockThreshold = if (tracksStock && tracked) lowStock else 0.0,
        isTracked = tracksStock && tracked,
        isFavourite = favourite
    )
'''


def main():
    validate()
    out = [HEADER]

    for key, display, desc, icon, fn, tracks, btype, bprefix in SHOPS:
        items = DATA[key]
        sku_prefix = key[:3]
        out.append("")
        out.append("    private fun %s(): List<ProductEntity> = listOf(" % fn)
        out.append(render_products(key, items, sku_prefix, bprefix, tracks))
        out.append('    ).map { it.toEntity("%s", %s) }' % (key, "true" if tracks else "false"))

    out.append("")
    out.append("    val shopTypes: List<ShopTypePreset> by lazy {")
    out.append("        listOf(")
    for key, display, desc, icon, fn, tracks, btype, bprefix in SHOPS:
        out.append(
            '            ShopTypePreset("%s", "%s", "%s", "%s", "%s", %s, %s()),'
            % (key, kt_escape(display), kt_escape(desc), icon, btype, "true" if tracks else "false", fn)
        )
    out.append("        )")
    out.append("    }")
    out.append("")
    out.append("    val defaultShopTypeKey: String = shopTypes.first().key")
    out.append("")
    out.append("    fun findShopType(key: String?): ShopTypePreset? =")
    out.append("        shopTypes.firstOrNull { it.key.equals(key ?: \"\", ignoreCase = true) }")
    out.append("")
    out.append("    fun productsFor(key: String?): List<ProductEntity> =")
    out.append("        findShopType(key)?.products ?: emptyList()")
    out.append("")
    out.append("    fun displayNameFor(key: String?): String =")
    out.append("        findShopType(key)?.displayName ?: \"My Shop\"")
    out.append("")
    out.append("    fun categoriesFor(key: String?): List<String> =")
    out.append("        findShopType(key)?.categories ?: emptyList()")
    out.append("}")
    out.append("")

    with open(OUT, "w") as f:
        f.write("\n".join(out))
    print("Wrote", OUT)


if __name__ == "__main__":
    main()
