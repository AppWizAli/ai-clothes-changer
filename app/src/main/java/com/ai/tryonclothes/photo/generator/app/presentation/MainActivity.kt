package com.ai.tryonclothes.photo.generator.app.presentation

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.ai.tryonclothes.photo.generator.app.R
import com.ai.tryonclothes.photo.generator.app.databinding.ActivityMainBinding
import com.ai.tryonclothes.photo.generator.app.presentation.common.show

/** Single-activity host. The bottom bar is only shown on the three top-level destinations. */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val topLevel = setOf(R.id.homeFragment, R.id.looksFragment, R.id.settingsFragment)
    private var bottomInset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = (supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
        binding.bottomNav.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.show(destination.id in topLevel)
            applyBottomInset()
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            bottomInset = bars.bottom
            binding.navHost.setPadding(bars.left, bars.top, bars.right, 0)
            binding.bottomNav.setPadding(bars.left, 0, bars.right, 0)
            applyBottomInset()
            insets
        }
    }

    /** Content sits above the system nav bar; when the bottom bar is visible it absorbs that inset itself. */
    private fun applyBottomInset() {
        val barVisible = binding.bottomNav.visibility == android.view.View.VISIBLE
        binding.navHost.setPadding(binding.navHost.paddingLeft, binding.navHost.paddingTop, binding.navHost.paddingRight, if (barVisible) 0 else bottomInset)
        binding.bottomNav.setPadding(binding.bottomNav.paddingLeft, 0, binding.bottomNav.paddingRight, if (barVisible) bottomInset else 0)
    }
}
