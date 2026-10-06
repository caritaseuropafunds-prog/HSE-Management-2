package com.hse.management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HseViewModel(private val dao: HseDao) : ViewModel() {
    val records = dao.records().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val observations = dao.observationCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val incidents = dao.incidentCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val audits = dao.auditCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val capa = dao.capaCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    fun add(r: HseRecord) = viewModelScope.launch { val id = dao.insert(r); dao.log(AuditLog(username=r.createdBy, action="Created", module=r.module, recordId=id)) }
    fun delete(r: HseRecord) = viewModelScope.launch { dao.delete(r); dao.log(AuditLog(username=r.createdBy, action="Deleted", module=r.module, recordId=r.id)) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = HseDatabase.get(this).dao()
        setContent { HseApp(dao) }
    }
}

@Composable
fun HseApp(dao: HseDao) {
    val vm: HseViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HseViewModel(dao) as T
    })
    var tab by remember { mutableIntStateOf(0) }
    var newModule by remember { mutableStateOf<String?>(null) }
    val tabs = listOf("Dashboard", "Observations", "Incidents", "Audits", "CAPA", "More")
    val icons = listOf(Icons.Default.Dashboard, Icons.Default.Visibility, Icons.Default.Warning, Icons.Default.AssignmentTurnedIn, Icons.Default.Assignment, Icons.Default.MoreHoriz)
    Scaffold(topBar = { TopAppBar(title = { Text("HSE Management System", fontWeight = FontWeight.Bold) }) }, bottomBar = {
        NavigationBar { tabs.forEachIndexed { i, label -> NavigationBarItem(selected=tab==i, onClick={tab=i}, icon={Icon(icons[i], null)}, label={Text(label)}) } }
    }) { p ->
        Box(Modifier.padding(p).fillMaxSize()) {
            when(tab) {
                0 -> Dashboard(vm)
                1 -> Register(vm, "Observation", "HSE Observations") { newModule="Observation" }
                2 -> Register(vm, "Incident", "Incident Investigations") { newModule="Incident" }
                3 -> Register(vm, "Audit", "Audit Register") { newModule="Audit" }
                4 -> Register(vm, "CAPA", "CAPA Register") { newModule="CAPA" }
                else -> More()
            }
            newModule?.let { module -> RecordDialog(module, { newModule=null }) { vm.add(it); newModule=null } }
        }
    }
}

@Composable fun Dashboard(vm: HseViewModel) {
    val o by vm.observations.collectAsState(); val i by vm.incidents.collectAsState(); val a by vm.audits.collectAsState(); val c by vm.capa.collectAsState()
    LazyColumn(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("HSE Performance Dashboard", style=MaterialTheme.typography.headlineSmall) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) { Metric("Observations",o); Metric("Incidents",i) } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) { Metric("Audits",a); Metric("CAPA",c) } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("HSE Workflow", fontWeight=FontWeight.Bold); Text("Observation / Incident / Audit → Root Cause → CAPA → Corrective Action → Verification → Closeout") } } }
    }
}

@Composable fun RowScope.Metric(name: String, value: Int) { Card(Modifier.weight(1f)) { Column(Modifier.padding(16.dp)) { Text(name); Text(value.toString(), style=MaterialTheme.typography.headlineMedium, fontWeight=FontWeight.Bold) } } }

@Composable fun Register(vm: HseViewModel, module: String, title: String, onAdd: () -> Unit) {
    val all by vm.records.collectAsState(); val list = all.filter { it.module == module }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) { Text(title, style=MaterialTheme.typography.headlineSmall); Button(onClick=onAdd) { Text("New") } }
        Spacer(Modifier.height(10.dp))
        if (list.isEmpty()) Text("No records yet. Tap New to create one.", Modifier.padding(12.dp)) else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) { items(list, key={it.id}) { r -> RecordCard(r) { vm.delete(r) } } }
    }
}

@Composable fun RecordCard(r: HseRecord, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) { Text(r.number, fontWeight=FontWeight.Bold); Text(r.title, style=MaterialTheme.typography.titleMedium); Text("${r.type} • ${r.category}"); if(r.responsible.isNotBlank()) Text("Responsible: ${r.responsible}"); if(r.targetDate.isNotBlank()) Text("Target: ${r.targetDate}"); if(r.description.isNotBlank()) Text(r.description); TextButton(onClick=onDelete) { Text("Delete") } } }
}

@Composable fun RecordDialog(module: String, onDismiss: () -> Unit, onSave: (HseRecord) -> Unit) {
    var title by remember { mutableStateOf("") }; var type by remember { mutableStateOf(if(module=="Observation") "Unsafe Condition" else if(module=="Incident") "Near Miss" else if(module=="Audit") "Internal Audit" else "Corrective Action") }; var category by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }; var responsible by remember { mutableStateOf("") }; var priority by remember { mutableStateOf("Medium") }; var target by remember { mutableStateOf("") }; var action by remember { mutableStateOf("") }; var root by remember { mutableStateOf("") }; var rca by remember { mutableStateOf("") }; var standard by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss, title={Text("New $module")}, text={ Column(verticalArrangement=Arrangement.spacedBy(6.dp)) { Field("Title",title){title=it}; Field("Type",type){type=it}; Field("Category / Finding Type",category){category=it}; Field("Description / Finding",description){description=it}; Field("Responsible",responsible){responsible=it}; Field("Priority",priority){priority=it}; Field("Target Date",target){target=it}; Field("Corrective Action",action){action=it}; Field("Root Cause",root){root=it}; if(module=="Incident") Field("RCA Method",rca){rca=it}; if(module=="Audit") Field("Standard / Clause",standard){standard=it} } }, confirmButton={Button(enabled=title.isNotBlank(), onClick={onSave(HseRecord(number="HSE-${System.currentTimeMillis()}",module=module,title=title,type=type,category=category,description=description,responsible=responsible,priority=priority,targetDate=target,correctiveAction=action,rootCause=root,investigationMethod=rca,standard=standard))}){Text("Save")}}, dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable fun Field(label:String, value:String, onChange:(String)->Unit) { OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=false) }

@Composable fun More() { Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) { Text("Administration", style=MaterialTheme.typography.headlineSmall); Text("Offline HSE database, observations, incidents, audits, CAPA and audit logging are included in this foundation."); Text("The next production modules should add STOP cards, evidence, RCA methods, audit workflow, CAPA source links, reports, master data, backup/restore, roles, notifications and risk/PTW/training features.") } }
