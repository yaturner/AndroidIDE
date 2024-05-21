package com.itsaky.androidide.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import androidx.fragment.app.viewModels
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.itsaky.androidide.R
import com.itsaky.androidide.activities.MainActivity
import com.itsaky.androidide.activities.PreferencesActivity
import com.itsaky.androidide.activities.TerminalActivity
import com.itsaky.androidide.adapters.MainActionsListAdapter
import com.itsaky.androidide.app.BaseApplication
import com.itsaky.androidide.app.BaseIDEActivity
import com.itsaky.androidide.common.databinding.LayoutDialogProgressBinding
import com.itsaky.androidide.databinding.FragmentMainBinding
import com.itsaky.androidide.models.MainScreenAction
import com.itsaky.androidide.preferences.databinding.LayoutDialogTextInputBinding
import com.itsaky.androidide.preferences.internal.GITHUB_PAT
import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.roomData.MessageDao
import com.itsaky.androidide.roomData.MessageRoomDatabase
import com.itsaky.androidide.tasks.runOnUiThread
import com.itsaky.androidide.utils.DialogUtils
import com.itsaky.androidide.utils.Environment
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashSuccess
import com.itsaky.androidide.viewmodel.MainViewModel
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.CloneCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.slf4j.LoggerFactory
import java.io.File
import java.text.MessageFormat
import java.util.concurrent.CancellationException

class MainFragment : BaseFragment() {

  private val viewModel by viewModels<MainViewModel>(
    ownerProducer = { requireActivity() })
  private var binding: FragmentMainBinding? = null

  companion object {

    private val log = LoggerFactory.getLogger(MainFragment::class.java)
  }

  private lateinit var fab : FloatingActionButton
  private val applicationScope = CoroutineScope(SupervisorJob())
  private val messageRoomDatabase : MessageRoomDatabase by lazy {
      MessageRoomDatabase.getDatabase(requireContext(), applicationScope)
  }

  private val shareActivityResultLauncher = registerForActivityResult<Intent, ActivityResult>(
    ActivityResultContracts.StartActivityForResult()
  ) { //ACTION_SEND always returns RESULT_CANCELLED, ignore it
    // There are no request codes
  }

  private val handler : Handler = Handler(Looper.getMainLooper())

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = FragmentMainBinding.inflate(inflater, container, false)
    return binding!!.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    //////////////////FOR DEBUGGING ONLY////////////////////
//    context?.let {
//      it.deleteDatabase("Message_database")
//      MessageRoomDatabase.getDatabase(it, applicationScope)
//      messageRoomDatabase?.let { database -> applicationScope.launch { dumpDatabase(database) } }
//
//    }
    //////////////////FOR DEBUGGING ONLY////////////////////

    val actions = MainScreenAction.all().also { actions ->
      val onClick = { action: MainScreenAction, _: View ->
        when (action.id) {
          MainScreenAction.ACTION_CREATE_PROJECT -> showCreateProject()
          MainScreenAction.ACTION_OPEN_PROJECT -> pickDirectory()
          MainScreenAction.ACTION_CLONE_REPO -> cloneGitRepo()
          MainScreenAction.ACTION_OPEN_TERMINAL -> startActivity(
            Intent(requireActivity(), TerminalActivity::class.java))
          MainScreenAction.ACTION_PREFERENCES -> gotoPreferences()
          MainScreenAction.ACTION_DONATE -> BaseApplication.getBaseInstance().openDonationsPage()
          MainScreenAction.ACTION_DOCS -> BaseApplication.getBaseInstance().openDocs()
        }
      }
      val onLongClick = { action: MainScreenAction, _: View ->
        performOptionsMenuClick(action)
        true
      }

      actions.forEach { action ->
        action.onClick = onClick
        action.onLongClick = onLongClick

        if (action.id == MainScreenAction.ACTION_OPEN_TERMINAL) {
          action.onLongClick = { _: MainScreenAction, _: View ->
            val intent = Intent(requireActivity(), TerminalActivity::class.java).apply {
              putExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, true)
            }
            startActivity(intent)
            true
          }
        }
      }
    }

    binding!!.actions.adapter = MainActionsListAdapter(this, actions)
  }


  // this method will handle the onclick options click
  private fun performOptionsMenuClick(action: MainScreenAction) {
    // create object of PopupMenu and pass context and view where we want
    // to show the popup menu
    val view = action.view
    val key = resources.getString(action.text)
    var text : String = "implementaion imminate"
    GlobalScope.launch { text = getMessageFromKey(messageRoomDatabase, key) }

    val popupMenu = context?.let { android.widget.PopupMenu(it, view) }
    // add the menu
    popupMenu?.inflate(R.menu.ctx_menu)
    // implement on menu item click Listener
    popupMenu?.setOnMenuItemClickListener { item ->
        when (item?.itemId) {
          R.id.ctx_menu_main_action_help -> {
            val builder = context?.let { DialogUtils.newMaterialDialogBuilder(it) }
            builder?.setTitle("Help")
              ?.setMessage(text)
              ?.setPositiveButton(android.R.string.ok) { dialogInterface, _ ->
                dialogInterface.dismiss()}
              ?.create()
              ?.show()
            true
          }
          // in the same way you can implement others
          R.id.ctx_menu_main_action_feedback -> {
            performFeedbackAction(action)
            true
          }
        }
        false
      }
    popupMenu?.show()
  }

  private fun performFeedbackAction(action : MainScreenAction) {
    val builder = context?.let { it1 -> DialogUtils.newMaterialDialogBuilder(it1) }
    builder?.let { builder ->
      builder.setTitle("Alert!")
        .setMessage(HtmlCompat.fromHtml(getString(R.string.feedback_warning),
          HtmlCompat.FROM_HTML_MODE_COMPACT))
        .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
        .setPositiveButton(android.R.string.ok) { dialog, _ ->
          run {
            val stackTrace = Exception().stackTrace.asList().toString().replace(",", "\n")
            val sb = StringBuilder(resources.getString(R.string.feedback_message))
            sb.append("\n\n\n")
            sb.append("-------------stack trace----------\n")
            sb.append(stackTrace)
            val feedbackIntent = Intent(Intent.ACTION_SEND)
            val subject = MessageFormat.format(resources.getString(R.string.feedback_subject),
              "Main")
            /*To send an email you need to specify mailto: as URI using setData() method
                   and data type will be to text/plain using setType() method*/
            feedbackIntent.data = Uri.parse("mailto:")
            feedbackIntent.type = "text/plain"
            feedbackIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf("feedback@appdevforall.com"))
            feedbackIntent.putExtra(Intent.EXTRA_SUBJECT, subject)
            feedbackIntent.putExtra(Intent.EXTRA_TEXT, sb.toString())
            shareActivityResultLauncher.launch(feedbackIntent)
            dialog.dismiss()
          }
        }
        .create()
        .show()
    }
  }

  suspend fun dumpDatabase(database: MessageRoomDatabase) {
    Log.d("MessageRoomDatabase", database.MessageDao().getAlphabetizedMessages().toString())
  }

  suspend fun getMessageFromKey(database: MessageRoomDatabase, key : String) : String {
    return GlobalScope.async { database.MessageDao().getMessage(key) }.await()
  }

  override fun onDestroyView() {
    super.onDestroyView()
    binding = null
  }

  private fun pickDirectory() {
    pickDirectory(this::openProject)
  }

  private fun showCreateProject() {
    viewModel.setScreen(MainViewModel.SCREEN_TEMPLATE_LIST)
  }

  fun openProject(root: File) {
    (requireActivity() as MainActivity).openProject(root)
  }

  private fun cloneGitRepo() {
    val builder = DialogUtils.newMaterialDialogBuilder(requireContext())
    val binding = LayoutDialogTextInputBinding.inflate(layoutInflater)
    binding.name.setHint(string.git_clone_repo_url)

    builder.setView(binding.root)
    builder.setTitle(string.git_clone_repo)
    builder.setCancelable(true)
    builder.setPositiveButton(string.git_clone) { dialog, _ ->
      dialog.dismiss()
      val url = binding.name.editText?.text?.toString()
      doClone(url)
    }
    builder.setNegativeButton(android.R.string.cancel, null)
    builder.show()
  }

  private fun doClone(repo: String?) {
    if (repo.isNullOrBlank()) {
      log.warn("Unable to clone repo. Invalid repo URL : {}'", repo)
      return
    }

    var url = repo.trim()
    if (!url.endsWith(".git")) {
      url += ".git"
    }

    val builder = DialogUtils.newMaterialDialogBuilder(requireContext())
    val binding = LayoutDialogProgressBinding.inflate(layoutInflater)

    binding.message.visibility = View.VISIBLE

    builder.setTitle(string.git_clone_in_progress)
    builder.setMessage(url)
    builder.setView(binding.root)
    builder.setCancelable(false)

    val prefs = BaseApplication.getBaseInstance().prefManager
    val repoName = url.substringAfterLast('/').substringBeforeLast(".git")
    val targetDir = File(Environment.PROJECTS_DIR, repoName)
    if (targetDir.exists()) {
      showCloneDirExistsError(targetDir)
      return
    }

    val progress = GitCloneProgressMonitor(binding.progress, binding.message)
    val coroutineScope = (activity as? BaseIDEActivity?)?.activityScope ?: viewLifecycleScope

    var getDialog: Function0<AlertDialog?>? = null

    val cloneJob = coroutineScope.launch(Dispatchers.IO) {

      val git = try {
        val cmd: CloneCommand = Git.cloneRepository()
        cmd
          .setURI(url)
          .setDirectory(targetDir)
          .setProgressMonitor(progress)
        val token = prefs.getString(GITHUB_PAT, "")
        //error is caught in showCloneError
        if(!token.isNullOrBlank()) {
          cmd.setCredentialsProvider( UsernamePasswordCredentialsProvider("<token>", token))
        }
        cmd.call()
      } catch (err: Throwable) {
        if (!progress.isCancelled) {
          err.printStackTrace()
          withContext(Dispatchers.Main) {
            getDialog?.invoke()?.also { if (it.isShowing) it.dismiss() }
            showCloneError(err)
          }
        }
        null
      }

      try {
        git?.close()
      } finally {
        val success = git != null
        withContext(Dispatchers.Main) {
          getDialog?.invoke()?.also { dialog ->
            if (dialog.isShowing) dialog.dismiss()
            if (success) flashSuccess(string.git_clone_success)
          }
        }
      }
    }

    builder.setPositiveButton(android.R.string.cancel) { iface, _ ->
      iface.dismiss()
      progress.cancel()
      cloneJob.cancel(CancellationException("Cancelled by user"))
    }

    val dialog = builder.show()
    getDialog = { dialog }
  }

  private fun showCloneDirExistsError(targetDir: File) {
    val builder = context?.let { AlertDialog.Builder(it) }
    builder?.setTitle(string.title_warning)
    builder?.setMessage(string.git_clone_dir_exists)
    builder?.setPositiveButton(android.R.string.ok) { _, _ -> targetDir.deleteRecursively() }
    builder?.setNegativeButton(android.R.string.cancel) { dlg, _ -> dlg.dismiss() }
    builder?.show()
  }

  private fun showCloneError(error: Throwable?) {
    if (error == null) {
      flashError(string.git_clone_failed)
      return
    }

    val builder = DialogUtils.newMaterialDialogBuilder(requireContext())
    builder.setTitle(string.git_clone_failed)
    builder.setMessage(error.localizedMessage)
    builder.setPositiveButton(android.R.string.ok, null)
    builder.show()
  }

  private fun gotoPreferences() {
    startActivity(Intent(requireActivity(), PreferencesActivity::class.java))
  }

  // TODO(itsaky) : Improve this implementation
  class GitCloneProgressMonitor(val progress: LinearProgressIndicator,
    val message: TextView
  ) : ProgressMonitor {

    private var cancelled = false

    fun cancel() {
      cancelled = true
    }

    override fun start(totalTasks: Int) {
      runOnUiThread { progress.max = totalTasks }
    }

    override fun beginTask(title: String?, totalWork: Int) {
      runOnUiThread { message.text = title }
    }

    override fun update(completed: Int) {
      runOnUiThread { progress.progress = completed }
    }

    override fun showDuration(enabled: Boolean) {
      // no-op
    }

    override fun endTask() {}

    override fun isCancelled(): Boolean {
      return cancelled || Thread.currentThread().isInterrupted
    }
  }
}
