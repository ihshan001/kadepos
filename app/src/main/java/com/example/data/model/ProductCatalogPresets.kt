package com.example.data.model

object ProductCatalogPresets {

    data class ShopCategoryPreset(
        val key: String,
        val displayName: String,
        val description: String,
        val iconName: String,
        val products: List<ProductEntity>
    )

    val availableShopPresets = listOf(
        ShopCategoryPreset(
            key = "RETAIL_GROCERY",
            displayName = "Grocery & Retail",
            description = "Rice, milk powder, biscuits, soaps, cold drinks & daily essentials",
            iconName = "shopping_basket",
            products = listOf(
                ProductEntity(name = "Sunlight Lemon Soap 100g", sellingPrice = 120.0, costPrice = 95.0, barcode = "890103011111", category = "Personal Care", unit = "Piece", currentStock = 45.0, lowStockThreshold = 10.0, isFavourite = true),
                ProductEntity(name = "Munchee Super Cream Cracker 490g", sellingPrice = 380.0, costPrice = 310.0, barcode = "479202410102", category = "Biscuits", unit = "Packet", currentStock = 28.0, lowStockThreshold = 8.0, isFavourite = true),
                ProductEntity(name = "Anchor Full Cream Milk Powder 400g", sellingPrice = 1050.0, costPrice = 930.0, barcode = "941420010203", category = "Dairy", unit = "Packet", currentStock = 18.0, lowStockThreshold = 5.0, isFavourite = true),
                ProductEntity(name = "White Sugar 1kg", sellingPrice = 260.0, costPrice = 220.0, barcode = "4791001001", category = "Provisions", unit = "Kg", currentStock = 75.0, lowStockThreshold = 15.0, isFavourite = true),
                ProductEntity(name = "Samba Rice (Kiri Samba) 1kg", sellingPrice = 240.0, costPrice = 205.0, barcode = "4791001002", category = "Grains", unit = "Kg", currentStock = 120.0, lowStockThreshold = 20.0, isFavourite = true),
                ProductEntity(name = "Mysoor Dhal 500g", sellingPrice = 180.0, costPrice = 148.0, barcode = "4791001003", category = "Grains", unit = "Packet", currentStock = 60.0, lowStockThreshold = 10.0, isFavourite = true),
                ProductEntity(name = "Watawala Tea 200g", sellingPrice = 350.0, costPrice = 290.0, barcode = "479202511011", category = "Beverages", unit = "Packet", currentStock = 24.0, lowStockThreshold = 6.0, isFavourite = true),
                ProductEntity(name = "Coca Cola 500ml", sellingPrice = 180.0, costPrice = 145.0, barcode = "5449000000996", category = "Beverages", unit = "Bottle", currentStock = 36.0, lowStockThreshold = 12.0, isFavourite = true),
                ProductEntity(name = "Astra Margarine 250g", sellingPrice = 420.0, costPrice = 355.0, barcode = "890103022222", category = "Dairy", unit = "Tub", currentStock = 15.0, lowStockThreshold = 4.0),
                ProductEntity(name = "MD Mixed Fruit Jam 300g", sellingPrice = 390.0, costPrice = 325.0, barcode = "479201100301", category = "Spreads", unit = "Bottle", currentStock = 12.0, lowStockThreshold = 4.0),
                ProductEntity(name = "Clogard Toothpaste 120g", sellingPrice = 220.0, costPrice = 175.0, barcode = "479202422233", category = "Personal Care", unit = "Piece", currentStock = 20.0, lowStockThreshold = 6.0),
                ProductEntity(name = "Farm Fresh Eggs Large", sellingPrice = 45.0, costPrice = 38.0, barcode = "4791001004", category = "Provisions", unit = "Piece", currentStock = 150.0, lowStockThreshold = 30.0, isFavourite = true)
            )
        ),
        ShopCategoryPreset(
            key = "FOOD_RESTAURANT",
            displayName = "Restaurant, Cafe & Bakery",
            description = "Kottu, fried rice, tea, fresh juice, snacks & bakery items",
            iconName = "restaurant",
            products = listOf(
                ProductEntity(name = "Chicken Kottu Roti (Full)", sellingPrice = 850.0, costPrice = 480.0, category = "Mains", unit = "Portion", currentStock = 50.0, lowStockThreshold = 5.0, isFavourite = true),
                ProductEntity(name = "Egg Fried Rice (Regular)", sellingPrice = 650.0, costPrice = 360.0, category = "Mains", unit = "Portion", currentStock = 50.0, lowStockThreshold = 5.0, isFavourite = true),
                ProductEntity(name = "Plain Ceylon Tea (Ginger)", sellingPrice = 60.0, costPrice = 20.0, category = "Beverages", unit = "Cup", currentStock = 100.0, lowStockThreshold = 10.0, isFavourite = true),
                ProductEntity(name = "Fresh Milk Tea", sellingPrice = 120.0, costPrice = 55.0, category = "Beverages", unit = "Cup", currentStock = 80.0, lowStockThreshold = 10.0, isFavourite = true),
                ProductEntity(name = "Fish Bun (Maalu Paan)", sellingPrice = 90.0, costPrice = 50.0, category = "Bakery", unit = "Piece", currentStock = 30.0, lowStockThreshold = 6.0, isFavourite = true),
                ProductEntity(name = "Vegetable Roti (Elavalu)", sellingPrice = 80.0, costPrice = 40.0, category = "Short Eats", unit = "Piece", currentStock = 25.0, lowStockThreshold = 5.0, isFavourite = true),
                ProductEntity(name = "Egg Roti (Biththara)", sellingPrice = 120.0, costPrice = 65.0, category = "Short Eats", unit = "Piece", currentStock = 25.0, lowStockThreshold = 5.0),
                ProductEntity(name = "Iced Milo Dinosaur", sellingPrice = 350.0, costPrice = 180.0, category = "Beverages", unit = "Glass", currentStock = 40.0, lowStockThreshold = 8.0, isFavourite = true),
                ProductEntity(name = "Chicken Dum Biriyani", sellingPrice = 1250.0, costPrice = 720.0, category = "Mains", unit = "Portion", currentStock = 30.0, lowStockThreshold = 5.0, isFavourite = true),
                ProductEntity(name = "Chocolate Lava Cake", sellingPrice = 450.0, costPrice = 220.0, category = "Desserts", unit = "Piece", currentStock = 15.0, lowStockThreshold = 3.0)
            )
        ),
        ShopCategoryPreset(
            key = "PHARMACY",
            displayName = "Pharmacy & Healthcare",
            description = "Medicines, syrups, surgical supplies & wellness items",
            iconName = "local_pharmacy",
            products = listOf(
                ProductEntity(name = "Paracetamol 500mg (10s)", sellingPrice = 50.0, costPrice = 30.0, barcode = "890111122201", category = "General Medicine", unit = "Card", currentStock = 120.0, lowStockThreshold = 25.0, isFavourite = true),
                ProductEntity(name = "Vitamin C 500mg (10s)", sellingPrice = 120.0, costPrice = 78.0, barcode = "890111122202", category = "Supplements", unit = "Card", currentStock = 60.0, lowStockThreshold = 15.0, isFavourite = true),
                ProductEntity(name = "Surgical Face Mask (Pack 10)", sellingPrice = 150.0, costPrice = 90.0, barcode = "890111122203", category = "Surgical", unit = "Pack", currentStock = 50.0, lowStockThreshold = 10.0, isFavourite = true),
                ProductEntity(name = "Dettol Antiseptic 125ml", sellingPrice = 480.0, costPrice = 410.0, barcode = "890111122204", category = "First Aid", unit = "Bottle", currentStock = 24.0, lowStockThreshold = 6.0, isFavourite = true),
                ProductEntity(name = "Digital Body Thermometer", sellingPrice = 850.0, costPrice = 550.0, barcode = "890111122205", category = "Devices", unit = "Piece", currentStock = 15.0, lowStockThreshold = 4.0),
                ProductEntity(name = "Antacid Gel Suspension 200ml", sellingPrice = 320.0, costPrice = 235.0, barcode = "890111122206", category = "General Medicine", unit = "Bottle", currentStock = 20.0, lowStockThreshold = 5.0),
                ProductEntity(name = "Hand Sanitizer 100ml", sellingPrice = 250.0, costPrice = 165.0, barcode = "890111122207", category = "First Aid", unit = "Bottle", currentStock = 35.0, lowStockThreshold = 8.0),
                ProductEntity(name = "Waterproof Plasters (Box 20)", sellingPrice = 200.0, costPrice = 130.0, barcode = "890111122208", category = "First Aid", unit = "Box", currentStock = 40.0, lowStockThreshold = 10.0)
            )
        ),
        ShopCategoryPreset(
            key = "CLOTHING_FASHION",
            displayName = "Clothing & Boutique",
            description = "Shirts, t-shirts, dresses, denims & fashion accessories",
            iconName = "checkroom",
            products = listOf(
                ProductEntity(name = "Crewneck Cotton T-Shirt", sellingPrice = 1850.0, costPrice = 1100.0, barcode = "CLOTH001", category = "Men's Wear", unit = "Piece", currentStock = 25.0, lowStockThreshold = 5.0, isFavourite = true),
                ProductEntity(name = "Slim-Fit Denim Jeans", sellingPrice = 4500.0, costPrice = 2800.0, barcode = "CLOTH002", category = "Men's Wear", unit = "Piece", currentStock = 18.0, lowStockThreshold = 4.0, isFavourite = true),
                ProductEntity(name = "Floral Summer Dress", sellingPrice = 3200.0, costPrice = 1950.0, barcode = "CLOTH003", category = "Women's Wear", unit = "Piece", currentStock = 14.0, lowStockThreshold = 3.0, isFavourite = true),
                ProductEntity(name = "Formal Long Sleeve Shirt", sellingPrice = 2900.0, costPrice = 1700.0, barcode = "CLOTH004", category = "Men's Wear", unit = "Piece", currentStock = 20.0, lowStockThreshold = 5.0, isFavourite = true),
                ProductEntity(name = "Genuine Leather Belt", sellingPrice = 1200.0, costPrice = 650.0, barcode = "CLOTH005", category = "Accessories", unit = "Piece", currentStock = 22.0, lowStockThreshold = 6.0),
                ProductEntity(name = "Ankle Cotton Socks (3-Pack)", sellingPrice = 600.0, costPrice = 320.0, barcode = "CLOTH006", category = "Accessories", unit = "Pack", currentStock = 30.0, lowStockThreshold = 8.0)
            )
        ),
        ShopCategoryPreset(
            key = "ELECTRONICS_HARDWARE",
            displayName = "Electronics & Hardware",
            description = "Cables, chargers, bulbs, earphones & hardware tools",
            iconName = "devices",
            products = listOf(
                ProductEntity(name = "Fast Charging USB-C Cable 1m", sellingPrice = 650.0, costPrice = 280.0, barcode = "ELEC001", category = "Cables", unit = "Piece", currentStock = 35.0, lowStockThreshold = 8.0, isFavourite = true),
                ProductEntity(name = "20W PD Wall Charger Adapter", sellingPrice = 1650.0, costPrice = 850.0, barcode = "ELEC002", category = "Chargers", unit = "Piece", currentStock = 20.0, lowStockThreshold = 5.0, isFavourite = true),
                ProductEntity(name = "TWS Wireless Earbuds", sellingPrice = 3500.0, costPrice = 1900.0, barcode = "ELEC003", category = "Audio", unit = "Piece", currentStock = 15.0, lowStockThreshold = 3.0, isFavourite = true),
                ProductEntity(name = "Tempered Glass Screen Protector", sellingPrice = 450.0, costPrice = 150.0, barcode = "ELEC004", category = "Accessories", unit = "Piece", currentStock = 60.0, lowStockThreshold = 15.0, isFavourite = true),
                ProductEntity(name = "10000mAh Slim Power Bank", sellingPrice = 4200.0, costPrice = 2600.0, barcode = "ELEC005", category = "Power", unit = "Piece", currentStock = 12.0, lowStockThreshold = 3.0),
                ProductEntity(name = "9W LED Pin Bulb (Warm)", sellingPrice = 380.0, costPrice = 260.0, barcode = "HARD001", category = "Lighting", unit = "Piece", currentStock = 45.0, lowStockThreshold = 10.0, isFavourite = true),
                ProductEntity(name = "3-Way Multi Extension 2m", sellingPrice = 1450.0, costPrice = 920.0, barcode = "HARD002", category = "Electrical", unit = "Piece", currentStock = 16.0, lowStockThreshold = 4.0),
                ProductEntity(name = "AAA Alkaline Batteries (4-Pack)", sellingPrice = 550.0, costPrice = 380.0, barcode = "HARD003", category = "Electrical", unit = "Pack", currentStock = 28.0, lowStockThreshold = 6.0)
            )
        ),
        ShopCategoryPreset(
            key = "GENERAL_STORE",
            displayName = "General Store & Stationery",
            description = "Stationery, household supplies, snacks & quick goods",
            iconName = "storefront",
            products = listOf(
                ProductEntity(name = "Blue Ballpoint Pens (Pack 5)", sellingPrice = 150.0, costPrice = 90.0, barcode = "GEN001", category = "Stationery", unit = "Pack", currentStock = 40.0, lowStockThreshold = 10.0, isFavourite = true),
                ProductEntity(name = "A4 Exercise Book 120 Pages", sellingPrice = 220.0, costPrice = 150.0, barcode = "GEN002", category = "Stationery", unit = "Book", currentStock = 50.0, lowStockThreshold = 15.0, isFavourite = true),
                ProductEntity(name = "Facial Tissue Box 200 Sheets", sellingPrice = 280.0, costPrice = 195.0, barcode = "GEN003", category = "Household", unit = "Box", currentStock = 25.0, lowStockThreshold = 6.0, isFavourite = true),
                ProductEntity(name = "Windproof Automatic Umbrella", sellingPrice = 1250.0, costPrice = 750.0, barcode = "GEN004", category = "Household", unit = "Piece", currentStock = 18.0, lowStockThreshold = 4.0),
                ProductEntity(name = "Mineral Water 1.5L", sellingPrice = 140.0, costPrice = 95.0, barcode = "GEN005", category = "Beverages", unit = "Bottle", currentStock = 30.0, lowStockThreshold = 10.0, isFavourite = true),
                ProductEntity(name = "Ginger Biscuits 200g", sellingPrice = 250.0, costPrice = 190.0, barcode = "GEN006", category = "Snacks", unit = "Packet", currentStock = 22.0, lowStockThreshold = 6.0, isFavourite = true)
            )
        )
    )

    fun getProductsForShopKey(key: String): List<ProductEntity> {
        val found = availableShopPresets.find { it.key.equals(key, ignoreCase = true) }
        return found?.products ?: availableShopPresets.first().products
    }
}
