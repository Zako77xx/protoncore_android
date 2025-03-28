package me.proton.core.plan.presentation.view

import android.content.Context
import android.os.Build
import android.os.Build.VERSION_CODES
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.annotation.StyleRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import me.proton.core.plan.presentation.R
import me.proton.core.plan.presentation.databinding.DynamicEntitlementStorageViewBinding.inflate

@Suppress("MagicNumber")
class DynamicEntitlementProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val binding by lazy { inflate(LayoutInflater.from(context), this) }

    var text: CharSequence?
        get() = binding.text.text
        set(value) {
            binding.text.text = value
        }

    var progress: Int
        get() = binding.progress.progress
        set(value) {
            binding.progress.progress = value
            val indicatorColor = when {
                value < 50 -> ContextCompat.getColor(context, R.color.notification_success)
                value < 90 -> ContextCompat.getColor(context, R.color.notification_warning)
                else -> ContextCompat.getColor(context, R.color.notification_error)
            }
            binding.progress.setIndicatorColor(indicatorColor)
        }

    var progressMin: Int?
        get() = if (Build.VERSION.SDK_INT >= VERSION_CODES.O) { binding.progress.min } else { 0 }
        set(value) { if (Build.VERSION.SDK_INT >= VERSION_CODES.O) { binding.progress.min = value ?: 0 } }

    var progressMax: Int?
        get() = binding.progress.max
        set(value) {
            binding.progress.max = value ?: 100
        }
}
