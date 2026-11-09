import { common } from '@kit.AbilityKit';
import { webview } from '@kit.ArkWeb';
import { url } from '@kit.ArkTS';
import { photoAccessHelper } from '@kit.MediaLibraryKit';

const SITE_ORIGIN: string = 'https://154.36.185.110:18443';
const HOME: string = SITE_ORIGIN + '/';

function secureUrl(address: string): url.URL | undefined {
  try {
    const parsed = url.URL.parseURL(address);
    if (parsed.protocol === 'https:' && parsed.hostname && !parsed.username && !parsed.password) {
      return parsed;
    }
  } catch {
    // ArkWeb must not navigate to malformed URLs or pass them to the system.
  }
  return undefined;
}

@Entry
@Component
struct Index {
  @State private failure: string = '';
  @State private rendererLost: boolean = false;
  private controller: webview.WebviewController = new webview.WebviewController();
  private controllerReady: boolean = false;
  private startPage: string = HOME;
  private lastPage: string = HOME;
  private picking: boolean = false;

  private interceptNavigation(request: WebResourceRequest): boolean {
    const destination = secureUrl(request.getRequestUrl());
    if (destination?.origin === SITE_ORIGIN) {
      return false;
    }
    if (destination && request.isMainFrame() && request.isRequestGesture()) {
      const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
      context.openLink(destination.href).catch(() => {
        this.getUIContext().getPromptAction().showToast({ message: '无法打开外部链接' });
      });
    }
    return true;
  }

  private async selectPictures(event: OnShowFileSelectorEvent): Promise<void> {
    if (this.picking) {
      event.result.handleFileList([]);
      return;
    }
    this.picking = true;
    let uris: string[] = [];
    try {
      const options = new photoAccessHelper.PhotoSelectOptions();
      options.MIMEType = photoAccessHelper.PhotoViewMIMETypes.IMAGE_TYPE;
      options.maxSelectNumber = event.fileSelector.getMode() === FileSelectorMode.FileOpenMultipleMode ? 9 : 1;
      const result = await new photoAccessHelper.PhotoViewPicker().select(options);
      uris = result.photoUris;
    } catch {
      // Returning an empty selection also resets the web input when a picker is cancelled.
    } finally {
      this.picking = false;
      event.result.handleFileList(uris);
    }
  }

  private retry(): void {
    this.failure = '';
    if (this.rendererLost) {
      this.startPage = this.lastPage;
      this.controller = new webview.WebviewController();
      this.rendererLost = false;
    } else if (this.controllerReady) {
      this.controller.loadUrl(this.lastPage);
    }
  }

  onBackPress(): boolean {
    if (this.controllerReady && this.controller.accessBackward()) {
      this.failure = '';
      this.controller.backward();
      return true;
    }
    return false;
  }

  build() {
    Stack() {
      if (!this.rendererLost) {
        Web({ src: this.startPage, controller: this.controller })
          .width('100%')
          .height('100%')
          .javaScriptAccess(true)
          .domStorageAccess(true)
          .mixedMode(MixedMode.None)
          .fileAccess(false)
          .multiWindowAccess(false)
          .onControllerAttached(() => {
            this.controllerReady = true;
          })
          .onLoadIntercept((event: OnLoadInterceptEvent) => this.interceptNavigation(event.data))
          .onPageBegin((event: OnPageBeginEvent) => {
            if (secureUrl(event.url)?.origin === SITE_ORIGIN) {
              this.lastPage = event.url;
              this.failure = '';
            }
          })
          .onNavigationEntryCommitted((entry: LoadCommittedDetails) => {
            if (entry.isMainFrame && secureUrl(entry.url)?.origin === SITE_ORIGIN) {
              this.lastPage = entry.url;
            }
          })
          .onErrorReceive((event: OnErrorReceiveEvent) => {
            if (event.request.isMainFrame() && secureUrl(event.request.getRequestUrl())?.origin === SITE_ORIGIN &&
              event.error.getErrorCode() !== -3) {
              this.lastPage = event.request.getRequestUrl();
              if (!this.failure) {
                this.failure = '无法连接 LexAid，请检查网络后重试。';
              }
            }
          })
          .onHttpErrorReceive((event: OnHttpErrorReceiveEvent) => {
            if (event.request.isMainFrame() && secureUrl(event.request.getRequestUrl())?.origin === SITE_ORIGIN) {
              this.lastPage = event.request.getRequestUrl();
              this.failure = '服务暂时不可用（HTTP ' + event.response.getResponseCode() + '）。';
            }
          })
          .onSslErrorEvent((event: SslErrorEvent) => {
            event.handler.handleCancel();
            if (event.isMainFrame) {
              this.failure = '无法验证 LexAid 的安全证书。';
            }
          })
          .onShowFileSelector((event: OnShowFileSelectorEvent) => {
            this.selectPictures(event).catch(() => {
              // The requesting renderer may have exited while the gallery was open.
            });
            return true;
          })
          .onRenderExited(() => {
            this.controllerReady = false;
            this.failure = '页面已中断，请重新加载。';
            this.rendererLost = true;
          })
      }
      if (this.failure.length > 0) {
        Column({ space: 16 }) {
          Text(this.failure).fontSize(17).textAlign(TextAlign.Center)
          Button('重新加载').onClick(() => this.retry())
        }
        .justifyContent(FlexAlign.Center)
        .width('100%')
        .height('100%')
        .padding(24)
        .backgroundColor($r('app.color.start_window_background'))
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor($r('app.color.start_window_background'))
  }
}
