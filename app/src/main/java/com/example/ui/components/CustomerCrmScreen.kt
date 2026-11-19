package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.i18n.AppLang
import com.example.i18n.formatSar
import com.example.i18n.stringsFor
import com.example.model.Customer
import com.example.ui.theme.StatusDanger
import java.util.UUID

@Composable
fun CustomerCrmScreen(
    lang: AppLang,
    customers: List<Customer>,
    onBack: () -> Unit,
    onSaveCustomer: (Customer) -> Unit,
    onDeleteCustomer: (String) -> Unit
) {
    val s = stringsFor(lang)
    BackHandler(onBack = onBack)
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Customer?>(null) }

    val filtered = remember(customers, search) {
        val q = search.trim().lowercase()
        customers.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.phone.contains(q) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.close)
                }
                Text(s.customerDirectoryCrm, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Button(onClick = {
                editing = Customer("cust_${UUID.randomUUID().toString().take(8)}", "", "", "")
            }) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search name or 05xxxxxxxx...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filtered, key = { it.id }) { cust ->
                Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(cust.name, fontWeight = FontWeight.Bold)
                            Text("${cust.phone} • Visits: ${cust.visitsCount}", style = MaterialTheme.typography.bodySmall)
                            Text("Spent: ${formatSar(cust.totalSpent, lang)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            if (cust.dueBalance > 0.01) {
                                Text("Due Balance: ${formatSar(cust.dueBalance, lang)}", style = MaterialTheme.typography.labelMedium, color = StatusDanger, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row {
                            IconButton(onClick = { editing = cust }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                            IconButton(onClick = { onDeleteCustomer(cust.id) }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusDanger) }
                        }
                    }
                }
            }
        }
    }

    editing?.let { c ->
        var name by remember(c) { mutableStateOf(c.name) }
        var phone by remember(c) { mutableStateOf(c.phone) }
        var notes by remember(c) { mutableStateOf(c.notes) }
        var due by remember(c) { mutableStateOf(c.dueBalance.toString()) }

        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (c.name.isBlank()) "Add Customer" else c.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(s.customerName) }, singleLine = true)
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(s.customerPhone) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                    OutlinedTextField(value = due, onValueChange = { due = it }, label = { Text("Due Balance (SAR)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(s.orderNotes) })
                }
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        onSaveCustomer(c.copy(name = name, phone = phone, notes = notes, dueBalance = due.toDoubleOrNull() ?: c.dueBalance))
                        editing = null
                    }
                ) { Text(s.confirm) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(s.cancel) } }
        )
    }
}
