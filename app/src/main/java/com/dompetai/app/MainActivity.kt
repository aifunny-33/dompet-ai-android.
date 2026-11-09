
package com.dompetai.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.*
import android.database.sqlite.SQLiteDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

class MainActivity : Activity() {

    private lateinit var db: SQLiteDatabase
    private lateinit var balanceText: TextView
    private lateinit var incomeText: TextView
    private lateinit var expenseText: TextView
    private lateinit var history: LinearLayout
    private lateinit var chartView: DailyChartView

    data class ChartDay(
        val label: String,
        val income: Double,
        val expense: Double
    )

    private fun today(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = openOrCreateDatabase("dompet.db", MODE_PRIVATE, null)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                description TEXT,
                amount REAL,
                type TEXT,
                transaction_date TEXT
            )
        """)

        // Migrasi database lama tanpa menghapus transaksi
        var hasDate = false
        val columns = db.rawQuery("PRAGMA table_info(transactions)", null)

        while (columns.moveToNext()) {
            if (columns.getString(1) == "transaction_date") {
                hasDate = true
            }
        }
        columns.close()

        if (!hasDate) {
            db.execSQL(
                "ALTER TABLE transactions ADD COLUMN transaction_date TEXT"
            )
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(18, 18, 18))
        }

        val heading = TextView(this).apply {
            text = "DOMPET AI"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 20)
        }
        layout.addView(heading)

        balanceText = TextView(this).apply {
            textSize = 28f
            setTextColor(Color.WHITE)
            setPadding(0, 10, 0, 20)
        }
        layout.addView(balanceText)

        val dailyTitle = TextView(this).apply {
            text = "Ringkasan Hari Ini"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 10, 0, 10)
        }
        layout.addView(dailyTitle)

        incomeText = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.rgb(76, 200, 120))
            setPadding(0, 8, 0, 8)
        }
        layout.addView(incomeText)

        expenseText = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.rgb(255, 100, 100))
            setPadding(0, 8, 0, 16)
        }
        layout.addView(expenseText)

        // Grafik pemasukan dan pengeluaran
        val chartTitle = TextView(this).apply {
            text = "Grafik 7 Hari Terakhir"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 20, 0, 8)
        }
        layout.addView(chartTitle)

        chartView = DailyChartView()
        layout.addView(
            chartView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(240)
            )
        )

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
            addTransaction(
                description.text.toString(),
                amount.text.toString(),
                "IN"
            )
            description.text.clear()
            amount.text.clear()
        }

        expense.setOnClickListener {
            addTransaction(
                description.text.toString(),
                amount.text.toString(),
                "OUT"
            )
            description.text.clear()
            amount.text.clear()
        }

        setContentView(ScrollView(this).apply {
            addView(layout)
        })

        updateScreen()
    }

    // Mengambil data transaksi selama 7 hari terakhir
    private fun getWeeklyData(): List<ChartDay> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val labelFormat = SimpleDateFormat("dd/MM", Locale.US)

        val dates = mutableListOf<Pair<String, String>>()
        val calendar = Calendar.getInstance()

        calendar.add(Calendar.DAY_OF_YEAR, -6)

        repeat(7) {
            dates.add(
                Pair(
                    dateFormat.format(calendar.time),
                    labelFormat.format(calendar.time)
                )
            )
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val totals = mutableMapOf<String, MutableList<Double>>()

        for ((date, _) in dates) {
            totals[date] = mutableListOf(0.0, 0.0)
        }

        val cursor = db.rawQuery(
            """
            SELECT transaction_date, type, SUM(amount)
            FROM transactions
            WHERE transaction_date BETWEEN ? AND ?
            GROUP BY transaction_date, type
            """,
            arrayOf(dates.first().first, dates.last().first)
        )

        while (cursor.moveToNext()) {
            val date = cursor.getString(0)
            val type = cursor.getString(1)
            val total = cursor.getDouble(2)

            val values = totals[date] ?: continue

            if (type == "IN") {
                values[0] = total
            } else if (type == "OUT") {
                values[1] = total
            }
        }

        cursor.close()

        return dates.map { (date, label) ->
            val values = totals[date]!!
            ChartDay(label, values[0], values[1])
        }
    }

    // Tampilan grafik tanpa library tambahan
    inner class DailyChartView : View(this@MainActivity) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var days: List<ChartDay> = emptyList()

        private val green = Color.rgb(76, 200, 120)
        private val red = Color.rgb(255, 100, 100)
        private val gray = Color.rgb(130, 130, 130)

        fun setData(data: List<ChartDay>) {
            days = data
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val density = resources.displayMetrics.density
            val left = 38f * density
            val right = width - 10f * density
            val top = 42f * density
            val bottom = height - 35f * density

            // Legenda
            paint.style = Paint.Style.FILL
            paint.textSize = 11f * density

            paint.color = green
            canvas.drawRect(
                left, 8f * density,
                left + 10f * density, 18f * density,
                paint
            )
            paint.color = Color.WHITE
            canvas.drawText(
                "Pemasukan",
                left + 15f * density,
                18f * density,
                paint
            )

            val secondLegend = left + 105f * density

            paint.color = red
            canvas.drawRect(
                secondLegend, 8f * density,
                secondLegend + 10f * density,
                18f * density,
                paint
            )
            paint.color = Color.WHITE
            canvas.drawText(
                "Pengeluaran",
                secondLegend + 15f * density,
                18f * density,
                paint
            )

            if (days.isEmpty()) return

            var maxValue = 0.0
            for (day in days) {
                maxValue = maxOf(
                    maxValue,
                    day.income,
                    day.expense
                )
            }

            if (maxValue <= 0.0) {
                paint.color = Color.LTGRAY
                paint.textSize = 13f * density
                canvas.drawText(
                    "Belum ada transaksi dalam 7 hari terakhir",
                    left,
                    top + 35f * density,
                    paint
                )
                return
            }

            val chartHeight = bottom - top
            val chartWidth = right - left

            // Garis bantu grafik
            paint.color = Color.rgb(65, 65, 65)
            paint.strokeWidth = 1f * density

            for (i in 0..4) {
                val y = top + chartHeight * i / 4f
                canvas.drawLine(left, y, right, y, paint)
            }

            val groupWidth = chartWidth / 7f
            val barWidth = groupWidth * 0.25f
            val gap = groupWidth * 0.06f

            for (i in days.indices) {
                val day = days[i]
                val center = left + groupWidth * (i + 0.5f)

                val incomeHeight =
                    (day.income / maxValue * chartHeight).toFloat()

                val expenseHeight =
                    (day.expense / maxValue * chartHeight).toFloat()

                // Batang pemasukan
                paint.color = green
                canvas.drawRoundRect(
                    center - gap / 2f - barWidth,
                    bottom - incomeHeight,
                    center - gap / 2f,
                    bottom,
                    4f * density,
                    4f * density,
                    paint
                )

                // Batang pengeluaran
                paint.color = red
                canvas.drawRoundRect(
                    center + gap / 2f,
                    bottom - expenseHeight,
                    center + gap / 2f + barWidth,
                    bottom,
                    4f * density,
                    4f * density,
                    paint
                )

                // Label tanggal
                paint.color = Color.LTGRAY
                paint.textSize = 9f * density
                paint.textAlign = Paint.Align.CENTER

                canvas.drawText(
                    day.label,
                    center,
                    height - 10f * density,
                    paint
                )
            }

            paint.textAlign = Paint.Align.LEFT
        }
    }

    private fun addTransaction(
        desc: String,
        value: String,
        type: String
    ) {
        val number = value.toDoubleOrNull()

        if (desc.isBlank() || number == null || number <= 0) {
            Toast.makeText(
                this,
                "Isi keterangan dan jumlah dengan benar",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        db.execSQL(
            """
            INSERT INTO transactions
            (description, amount, type, transaction_date)
            VALUES (?, ?, ?, ?)
            """,
            arrayOf(desc, number, type, today())
        )

        updateScreen()
    }

    private fun editTransaction(
        id: Int,
        desc: String,
        amount: Double,
        type: String
    ) {
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

            onItemSelectedListener =
                object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: android.widget.AdapterView<*>?,
                        view: android.view.View?,
                        position: Int,
                        id: Long
                    ) {
                        selectedType = position
                    }

                    override fun onNothingSelected(
                        parent: android.widget.AdapterView<*>?
                    ) {}
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
                val newAmount =
                    inputAmount.text.toString().toDoubleOrNull()

                if (
                    newDesc.isBlank() ||
                    newAmount == null ||
                    newAmount <= 0
                ) {
                    Toast.makeText(
                        this,
                        "Data tidak valid",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                val newType =
                    if (selectedType == 0) "IN" else "OUT"

                db.execSQL(
                    """
                    UPDATE transactions
                    SET description=?, amount=?, type=?
                    WHERE id=?
                    """,
                    arrayOf(newDesc, newAmount, newType, id)
                )

                updateScreen()
            }
            .show()
    }

    private fun deleteTransaction(id: Int) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Transaksi?")
            .setMessage(
                "Transaksi ini akan dihapus secara permanen."
            )
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
            """
            SELECT
            SUM(CASE WHEN type='IN' THEN amount ELSE -amount END)
            FROM transactions
            """,
            null
        )

        if (cursor.moveToFirst() && !cursor.isNull(0)) {
            balance = cursor.getDouble(0)
        }
        cursor.close()

        balanceText.text =
            "Saldo: Rp ${"%,.0f".format(balance)}"

        var dailyIncome = 0.0
        var dailyExpense = 0.0

        val daily = db.rawQuery(
            """
            SELECT type, SUM(amount)
            FROM transactions
            WHERE transaction_date=?
            GROUP BY type
            """,
            arrayOf(today())
        )

        while (daily.moveToNext()) {
            val type = daily.getString(0)
            val total = daily.getDouble(1)

            if (type == "IN") dailyIncome = total
            if (type == "OUT") dailyExpense = total
        }
        daily.close()

        incomeText.text =
            "Uang Masuk Hari Ini: Rp ${"%,.0f".format(dailyIncome)}"

        expenseText.text =
            "Uang Keluar Hari Ini: Rp ${"%,.0f".format(dailyExpense)}"

        // Perbarui grafik setiap ada perubahan transaksi
        chartView.setData(getWeeklyData())

        history.removeAllViews()

        val records = db.rawQuery(
            """
            SELECT id, description, amount, type
            FROM transactions
            ORDER BY id DESC
            """,
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
