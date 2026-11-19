package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.i18n.AppLang
import com.example.i18n.formatSar
import com.example.i18n.stringsFor
import com.example.model.PosOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HeldOrdersScreen(
    lang: AppLang,
    heldOrders: List<PosOrder>,
    onBack: () -> Unit,
    onRecall: (PosOrder) -> Unit,
    onDiscard: (String) -> Unit
) {
    val s = stringsFor(lang)
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.close)
                }
                Text(s.heldOrders, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Badge { Text("${heldOrders.size}") }
        }

        if (heldOrders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PauseCircleOutline, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Text(
                        text = if (lang == AppLang.AR) "لا توجد طلبات معلقة" else "No held or advance orders",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        } else {
            val df = SimpleDateFormat("HH:mm", Locale.US)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 360.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(heldOrders, key = { it.id }) { order ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${order.orderNumber} • ${order.customerName.ifBlank { "Customer" }}",
                                        fontWeight = FontWeight.Bold
                                    )
                                    val subDetails = listOfNotNull(
                                        df.format(Date(order.createdAt)),
                                        order.sourceDisplayName(lang),
                                        order.tableOrCarNumber.takeIf { it.isNotBlank() },
                                        order.customerPhone.takeIf { it.isNotBlank() }
                                    ).joinToString(" • ")
                                    Text(
                                        text = subDetails,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = formatSar(order.grandTotal, lang),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = order.items.joinToString(" • ") { "${it.qty}x ${it.displayName(lang)}" },
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onDiscard(order.id) },
                                    modifier = Modifier.weight(0.7f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(s.deleteOrder)
                                }
                                Button(
                                    onClick = { onRecall(order) },
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .testTag("recall_order_${order.id}")
                                ) {
                                    Icon(Icons.Default.ShoppingCartCheckout, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(s.recallOrder, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
