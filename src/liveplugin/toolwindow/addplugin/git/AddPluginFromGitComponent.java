package liveplugin.toolwindow.addplugin.git;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import git4idea.checkout.GitCheckoutProvider;
import git4idea.commands.Git;
import icons.GithubIcons;
import liveplugin.IDEUtil;
import liveplugin.LivePluginAppComponent;
import liveplugin.pluginrunner.GroovyPluginRunner;
import liveplugin.toolwindow.PluginToolWindowManager;
import liveplugin.toolwindow.RefreshPluginTreeAction;
import liveplugin.toolwindow.util.PluginsIO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

@SuppressWarnings("ComponentNotRegistered")
public class AddPluginFromGitComponent implements ApplicationComponent {
	@Override public void initComponent() {
		PluginToolWindowManager.addFromGitHubAction = new AddPluginFromGitAction();
	}

	@Override public void disposeComponent() {
	}

	@NotNull @Override public String getComponentName() {
		return "AddPluginFromGitComponent";
	}

	/**
	 * Partially copied from org.jetbrains.plugins.github.GithubCheckoutProvider (became com.intellij.dvcs.ui.CloneDvcsDialog in IJ13)
	 */
	private static class AddPluginFromGitAction extends AnAction {
		private static final Logger LOG = Logger.getInstance(AddPluginFromGitAction.class);

		private AddPluginFromGitAction() {
			super("Plugin from Git", "Plugin from Git", GithubIcons.Github_icon);
		}

		@Override public void actionPerformed(@NotNull AnActionEvent event) {
			GitCloneDialog dialog = new GitCloneDialog(event.getProject());
			dialog.show();
			if (!dialog.isOK()) return;
			dialog.rememberSettings();

			VirtualFile destinationFolder = LocalFileSystem.getInstance().findFileByIoFile(new File(dialog.getParentDirectory()));
			if (destinationFolder == null) return;

			GitCheckoutProvider.clone(
					event.getProject(),
					ServiceManager.getService(Git.class),
					new MyCheckoutListener(event.getProject(), destinationFolder, dialog.getDirectoryName()),
					destinationFolder,
					dialog.getSourceRepositoryURL(),
					dialog.getDirectoryName(),
					dialog.getParentDirectory()
			);
		}

		private static class MyCheckoutListener implements CheckoutProvider.Listener {
			private final VirtualFile destinationFolder;
			private final String pluginName;
			private final Project project;

			public MyCheckoutListener(@Nullable Project project, VirtualFile destinationFolder, String pluginName) {
				this.destinationFolder = destinationFolder;
				this.pluginName = pluginName;
				this.project = project;
			}

			/**
			 * Copied from {@link com.intellij.openapi.vcs.checkout.CompositeCheckoutListener}
			 */
			private static VirtualFile refreshVFS(final File directory) {
				final Ref<VirtualFile> result = new Ref<VirtualFile>();
				ApplicationManager.getApplication().runWriteAction(new Runnable() {
					public void run() {
						final LocalFileSystem lfs = LocalFileSystem.getInstance();
						final VirtualFile vDir = lfs.refreshAndFindFileByIoFile(directory);
						result.set(vDir);
						if (vDir != null) {
							final LocalFileSystem.WatchRequest watchRequest = lfs.addRootToWatch(vDir.getPath(), true);
							((NewVirtualFile)vDir).markDirtyRecursively();
							vDir.refresh(false, true);
							if (watchRequest != null) {
								lfs.removeWatchedRoot(watchRequest);
							}
						}
					}
				});
				return result.get();
			}

			@Override public void directoryCheckedOut(File directory, VcsKey vcs) {
				refreshVFS(directory);
			}

			@Override public void checkoutCompleted() {
				VirtualFile pluginsRoot = VirtualFileManager.getInstance().findFileByUrl("file://" + LivePluginAppComponent.pluginsRootPath());
				if (pluginsRoot == null) return;

				RefreshQueue.getInstance().refresh(false, true, new Runnable() {
					@Override public void run() {
						VirtualFile clonedFolder = destinationFolder.findChild(pluginName);
						if (clonedFolder == null) {
							LOG.error("Couldn't find virtual file for checked out plugin: " + pluginName);
							return;
						}

						try {

							if (LivePluginAppComponent.isInvalidPluginFolder(clonedFolder) && userDoesNotWantToKeepIt()) {
								PluginsIO.delete(clonedFolder.getPath());
							}

						} catch (Exception e) {
							if (project != null) {
								IDEUtil.showErrorDialog(project, "Error deleting plugin \"" + clonedFolder.getPath(), "Delete Plugin");
							}
							LOG.error(e);
						}

						RefreshPluginTreeAction.refreshPluginTree();
					}
				}, pluginsRoot);
			}

			private boolean userDoesNotWantToKeepIt() {
				int answer = Messages.showYesNoDialog(
						project,
						"It looks like \"" + pluginName + "\" is not a valid plugin because it does not contain \"" +
								GroovyPluginRunner.MAIN_SCRIPT + "\".\n\nDo you want to add it anyway?",
						"Add Plugin",
						Messages.getQuestionIcon()
				);
				return answer != Messages.YES;
			}
		}
	}
}
