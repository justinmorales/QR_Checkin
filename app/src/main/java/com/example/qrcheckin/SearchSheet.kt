package com.example.qrcheckin

import android.app.Activity
import android.widget.Toast
import android.content.Context
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.*

/*class SearchSheet {
    companion object {

        fun searchInSheet(context: Context, key: String): String {
            // Replace these values with your own
            val spreadsheetId = R.string.Sheet_ID
            val rangeToSearch =
                context.getString(R.string.form_master_list) + "!" + context.getString(R.string.Submission_ID_Column) + ":" + context.getString(R.string.Submission_ID_Column)
            //val searchValue = key

            // Build the Sheets service
            val sheetsService = buildSheetsService()

            // Build the request to search for the value
            val request = Sheets.Spreadsheets.Values.Get(spreadsheetId, rangeToSearch)
                .setValueRenderOption("FORMATTED_VALUE")
                .setDateTimeRenderOption("FORMATTED_STRING")
                .setMajorDimension("ROWS")
                .setQ("select * where A = '$key'")

            // Execute the request
            val response = request.execute()

            // Get the values from the response
            val values: List<List<Any>>? = response.getValues()

            // Print the matching rows
            if (values != null && values.isNotEmpty()) {
                for (row in values) {
                    println(values)
                }
            } else {
                println("No matching rows found.")
            }
        }

        fun buildSheetsService(): Sheets {
            // Replace this with the path to your credentials file
            val credentialsPath = "/path/to/your/credentials.json"

            // TODO: Initialize Google Sheets service with your credentials
            // You will need to set up the Sheets API in your project and include the necessary dependencies.

            return Sheets.Builder(/* your builder configuration */)
                .setApplicationName("Your Application Name")
                .build()
        }

    }

}*/