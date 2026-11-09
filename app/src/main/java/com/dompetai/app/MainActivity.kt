
package com.dompetai.app

import android.app.Activity
import android.app.AlertDialog
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

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(18, 18, 18))
        }

        balanceText = TextView(this).apply {
            textSize = 28f
            setTextColor(Color.WHITE)
        }
        layout.addView(balanceText)

        val description = EditText(this).apply {
            hint = "Keterangan transaksi"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        layout.addView(description)

        val amount = EditText(this).apply {
            hint = "Jumlah uang (Rp)"
            inputType = 2
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        layout.addView(amount)

        val income = Button(this).apply {
            text = "Tambah Pemasukan"
        }
        layout.addView(income)

        val expense = Button(this).apply {
            text = "Tambah Pengeluaran"
        }
        layout.addView(expense)

        val title = TextView(this).apply {
            text = "Riwayat Transaksi"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 20, 0, 10)
        }
        layout.addView(title)

        history = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
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

    private fun editTransaction(id: Int, desc: String, amount: Double, type: String) {
        val inputDesc = EditText(this).apply {
            setText(desc)
            hint = "Keterangan transaksi"
        }

        val inputAmount = EditText(this).apply {
            setText(amount.toLong().toString())
            hint = "Jumlah uang"
            inputType = 2
        }

        val typeOptions = arrayOf("Pemasukan", "Pengeluaran")
        var selectedType = if (type == "IN") 0 else 1

        val typeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                typeOptions
            )
            setSelection(selectedType)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    selectedType = position
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 10, 30, 10)
            addView(inputDesc)
            addView(inputAmount)
            addView(typeSpinner)
        }

        AlertDialog.Builder(this)
            .setTitle("Edit Transaksi")
            .setView(form)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan") { _, _ ->
                val newDesc = inputDesc.text.toString().trim()
                val newAmount = inputAmount.text.toString().toDoubleOrNull()

                if (newDesc.isBlank() || newAmount == null || newAmount <= 0) {
                    Toast.makeText(this, "Data tidak valid", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newType = if (selectedType == 0) "IN" else "OUT"

                db.execSQL(
                    "UPDATE transactions SET description=?, amount=?, type=? WHERE id=?",
                    arrayOf(newDesc, newAmount, newType, id)
                )

                updateScreen()
            }
            .show()
    }

    private fun deleteTransaction(id: Int) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Transaksi?")
            .setMessage("Transaksi ini akan dihapus secara permanen.")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Hapus") { _, _ ->
                db.execSQL(
                    "DELETE FROM transactions WHERE id=?",
                    arrayOf(id)
                )
                updateScreen()
            }
            .show()
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
            "SELECT id, description, amount, type FROM transactions ORDER BY id DESC",
            null
        )

        while (records.moveToNext()) {
            val id = records.getInt(0)
            val desc = records.getString(1)
            val amount = records.getDouble(2)
            val type = records.getString(3)

            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 12, 0, 12)
            }

            val item = TextView(this).apply {
                val sign = if (type == "IN") "+" else "-"
                text = "$desc : $sign Rp ${"%,.0f".format(amount)}"
                textSize = 16f
                setTextColor(Color.WHITE)
            }
            itemLayout.addView(item)

            val buttons = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val editButton = Button(this).apply {
                text = "Edit"
                setOnClickListener {
                    editTransaction(id, desc, amount, type)
                }
            }

            val deleteButton = Button(this).apply {
                text = "Hapus"
                setOnClickListener {
                    deleteTransaction(id)
                }
            }

            buttons.addView(editButton)
            buttons.addView(deleteButton)
            itemLayout.addView(buttons)

            history.addView(itemLayout)
        }

        records.close()
    }
}
