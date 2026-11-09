package com.dompetai.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import android.database.sqlite.SQLiteDatabase

class MainActivity : Activity() {

    private lateinit var db: SQLiteDatabase
    private lateinit var balanceText: TextView
    private lateinit var history: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = openOrCreateDatabase("dompet.db", MODE_PRIVATE, null)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                description TEXT,
                amount REAL,
                type TEXT
            )
        """)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(24, 24, 24, 24)
        layout.setBackgroundColor(Color.rgb(18, 18, 18))

        balanceText = TextView(this)
        balanceText.textSize = 28f
        balanceText.setTextColor(Color.WHITE)
        layout.addView(balanceText)

        val description = EditText(this)
        description.hint = "Keterangan transaksi"
        description.setTextColor(Color.WHITE)
        description.setHintTextColor(Color.GRAY)
        layout.addView(description)

        val amount = EditText(this)
        amount.hint = "Jumlah uang (Rp)"
        amount.inputType = 2
        amount.setTextColor(Color.WHITE)
        amount.setHintTextColor(Color.GRAY)
        layout.addView(amount)

        val income = Button(this)
        income.text = "Tambah Pemasukan"
        layout.addView(income)

        val expense = Button(this)
        expense.text = "Tambah Pengeluaran"
        layout.addView(expense)

        val title = TextView(this)
        title.text = "Riwayat Transaksi"
        title.textSize = 20f
        title.setTextColor(Color.WHITE)
        title.setPadding(0, 20, 0, 10)
        layout.addView(title)

        history = LinearLayout(this)
        history.orientation = LinearLayout.VERTICAL
        layout.addView(history)

        income.setOnClickListener {
            addTransaction(description.text.toString(), amount.text.toString(), "IN")
            description.text.clear()
            amount.text.clear()
        }

        expense.setOnClickListener {
            addTransaction(description.text.toString(), amount.text.toString(), "OUT")
            description.text.clear()
            amount.text.clear()
        }

        setContentView(ScrollView(this).apply {
            addView(layout)
        })

        updateScreen()
    }

    private fun addTransaction(desc: String, value: String, type: String) {
        val number = value.toDoubleOrNull()

        if (desc.isBlank() || number == null || number <= 0) {
            Toast.makeText(this, "Isi keterangan dan jumlah dengan benar", Toast.LENGTH_SHORT).show()
            return
        }

        db.execSQL(
            "INSERT INTO transactions (description, amount, type) VALUES (?, ?, ?)",
            arrayOf(desc, number, type)
        )

        updateScreen()
    }

    private fun updateScreen() {
        var balance = 0.0

        val cursor = db.rawQuery(
            "SELECT amount, type FROM transactions", null
        )

        while (cursor.moveToNext()) {
            val amount = cursor.getDouble(0)
            balance += if (cursor.getString(1) == "IN") amount else -amount
        }
        cursor.close()

        balanceText.text = "Saldo: Rp ${"%,.0f".format(balance)}"

        history.removeAllViews()

        val records = db.rawQuery(
            "SELECT description, amount, type FROM transactions ORDER BY id DESC",
            null
        )

        while (records.moveToNext()) {
            val desc = records.getString(0)
            val amount = records.getDouble(1)
            val type = records.getString(2)

            val item = TextView(this)
            val sign = if (type == "IN") "+" else "-"
            item.text = "$desc : $sign Rp ${"%,.0f".format(amount)}"
            item.textSize = 16f
            item.setTextColor(Color.WHITE)
            item.setPadding(0, 12, 0, 12)
            history.addView(item)
        }

        records.close()
    }
}
