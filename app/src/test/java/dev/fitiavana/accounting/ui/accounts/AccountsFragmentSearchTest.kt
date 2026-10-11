package dev.fitiavana.accounting.ui.accounts

import android.os.Looper
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class AccountsFragmentSearchTest {

    private val fragment = AccountsFragment()
    private lateinit var activity: FragmentActivity

    private fun launch() {
        activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment)
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun viewModel() =
        ViewModelProvider(fragment).get(AccountsViewModel::class.java)

    private fun createMenu(): MenuBuilder {
        val menu = MenuBuilder(activity)
        fragment.onCreateOptionsMenu(menu, SupportMenuInflater(activity))
        return menu
    }

    private fun searchView(menu: MenuBuilder) =
        menu.findItem(R.id.action_search).actionView as SearchView

    @Test
    fun `menu has a search item next to add`() {
        launch()

        val menu = createMenu()

        assertNotNull(menu.findItem(R.id.action_add_account))
        assertNotNull(searchView(menu))
    }

    @Test
    fun `typing in the search view updates the view model query`() {
        launch()
        val searchView = searchView(createMenu())

        searchView.setQuery("cash", false)

        assertEquals("cash", viewModel().searchQuery.value)
    }

    @Test
    fun `search view restores the query held by the view model`() {
        launch()
        viewModel().setSearchQuery("cash")

        val menu = createMenu()

        // Expansion itself needs a toolbar presenter, which a bare MenuBuilder lacks.
        assertEquals("cash", searchView(menu).query.toString())
    }

    @Test
    fun `no match shows the filtered empty message even with other filters off`() {
        launch()
        activity.findViewById<CheckBox>(R.id.checkbox_hide_zero_balance).isChecked = false
        viewModel().setSearchQuery("zzz-no-account-has-this-name")
        shadowOf(Looper.getMainLooper()).idle()

        val empty = activity.findViewById<TextView>(R.id.text_empty)
        assertEquals(View.VISIBLE, empty.visibility)
        assertEquals(activity.getString(R.string.empty_accounts_filtered), empty.text.toString())
    }
}
