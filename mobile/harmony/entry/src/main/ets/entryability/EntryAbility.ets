import { UIAbility } from '@kit.AbilityKit';
import { KeyboardAvoidMode, window } from '@kit.ArkUI';
import { hilog } from '@kit.PerformanceAnalysisKit';

export default class EntryAbility extends UIAbility {
  async onWindowStageCreate(stage: window.WindowStage): Promise<void> {
    const appWindow = stage.getMainWindowSync();
    try {
      await appWindow.setWindowLayoutFullScreen(false);
    } catch {
      hilog.warn(0x0000, 'LexAid', 'Using the default window safe area.');
    }
    try {
      await stage.loadContent('pages/Index');
      appWindow.getUIContext().setKeyboardAvoidMode(KeyboardAvoidMode.RESIZE);
    } catch {
      hilog.error(0x0000, 'LexAid', 'Failed to initialize the study window.');
    }
  }
}
