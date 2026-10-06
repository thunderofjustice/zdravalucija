package hr.zdravalucija

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import hr.zdravalucija.ui.LucijaApp
import hr.zdravalucija.ui.LucijaViewModel

class MainActivity : ComponentActivity() {
    private lateinit var vm: LucijaViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm = ViewModelProvider(this, LucijaViewModel.Factory((application as LucijaApplication).container))[LucijaViewModel::class.java]
        setContent { LucijaApp(vm) }
    }
    override fun onResume() { super.onResume(); if (::vm.isInitialized) vm.refreshDate() }
}
