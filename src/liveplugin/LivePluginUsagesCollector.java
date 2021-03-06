package liveplugin;

import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LivePluginUsagesCollector extends UsagesCollector {
	@NotNull @Override public Set<UsageDescriptor> getUsages(@Nullable Project project) throws CollectUsagesException {
		HashSet<UsageDescriptor> result = new HashSet<UsageDescriptor>();
		Settings settings = Settings.getInstance();
		for (Map.Entry<String, Integer> entry : settings.pluginsUsage.entrySet()) {
			result.add(new UsageDescriptor(entry.getKey(), entry.getValue()));
		}
		result.add(new UsageDescriptor("runAllPluginsOnIDEStartup", settings.runAllPluginsOnIDEStartup ? 1 : 0));
		return result;
	}

	@NotNull @Override public GroupDescriptor getGroupId() {
		return GroupDescriptor.create("Live Plugin", GroupDescriptor.LOWER_PRIORITY);
	}
}
