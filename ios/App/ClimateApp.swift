import SwiftUI
import WidgetKit

@main struct ClimateApp: App {
    var body: some Scene { WindowGroup { SetupView().preferredColorScheme(.dark) } }
}
@MainActor final class SetupModel: ObservableObject {
    @Published var token = ""
    @Published var country = "KR"
    @Published var region = "kic"
    @Published var devices: [ThinQDevice] = []
    @Published var message = "ThinQ와 같은 LG 계정의 PAT로 연결하세요."
    @Published var busy = false
    @Published var snapshot: Snapshot?
    @Published var hasCredentials = false
    private var pending: ThinQConfig?
    var isDemo: Bool { snapshot?.selection.id == DemoData.deviceID }
    private static let demoMessage = "체험 모드입니다. 표시되는 값은 실제 기기 값이 아닌 샘플입니다."
    init() {
        snapshot = ClimateStore().snapshot()
        if isDemo { message = Self.demoMessage }
        do { if let c = try ClimateStore().config() { country = c.country; region = c.region; hasCredentials = true; message = "저장된 계정으로 연결되어 있습니다. 아래에서 토큰을 교체할 수 있습니다." } }
        catch { message = "저장된 인증을 읽을 수 없습니다. 다시 연결해 주세요." }
    }
    func discover() async {
        guard !busy else { return }
        busy = true; devices = []; defer { busy = false }
        do {
            let old = try ClimateStore().config()
            let c = try ThinQConfig(token: token.isEmpty ? old?.token ?? "" : token, country: country, region: region, clientID: old?.clientID ?? "climate-" + UUID().uuidString)
            let found = try await ThinQClient(config: c).devices()
            guard let lock = StoreLock() else { message = "갱신 중입니다. 잠시 후 다시 시도해 주세요."; return }
            defer { withExtendedLifetime(lock) {} }
            try ClimateStore().replaceCredentials(c)
            devices = found; pending = c; token = ""; hasCredentials = true; snapshot = nil
            WidgetCenter.shared.reloadAllTimelines()
            message = devices.isEmpty ? "토큰을 저장했습니다. 지원되는 가습기가 없습니다. 계정과 기기 권한을 확인해 주세요." : "토큰을 저장했습니다. 위젯에 표시할 가습기를 선택하세요."
        } catch { message = (error as? ThinQError ?? .network).localizedDescription }
    }
    func select(_ device: ThinQDevice) async {
        guard let c = pending, !busy else { return }
        guard let lock = StoreLock() else { message = "갱신 중입니다. 잠시 후 다시 선택해 주세요."; return }
        do {
            try ClimateStore().save(c); ClimateStore().select(device); token = ""; devices = []; pending = nil
        } catch { message = "안전한 인증 저장에 실패했습니다. 앱 서명 설정을 확인해 주세요."; return }
        withExtendedLifetime(lock) {}
        // Release the cross-process lock before the network refresh.
        message = "연결됨 · 새로고침을 눌러 센서값을 받으세요."
        snapshot = ClimateStore().snapshot(); WidgetCenter.shared.reloadAllTimelines()
    }
    func startDemo() async {
        guard !busy, !hasCredentials else { return }
        do {
            guard let lock = StoreLock() else { message = "갱신 중입니다. 잠시 후 다시 시도해 주세요."; return }
            ClimateStore().select(DemoData.device)
            withExtendedLifetime(lock) {}
        }
        await refresh()
    }
    func refresh() async {
        guard !busy else { return }; busy = true; defer { busy = false }
        snapshot = await Refresher.refresh(); WidgetCenter.shared.reloadAllTimelines()
        message = isDemo ? Self.demoMessage : snapshot?.error ?? (snapshot == nil ? "먼저 가습기를 연결해 주세요." : "갱신 요청 완료")
    }
    func disconnect() {
        guard !busy, let lock = StoreLock() else { message = "갱신 중입니다. 잠시 후 다시 시도해 주세요."; return }
        defer { withExtendedLifetime(lock) {} }
        do { try ClimateStore().clear(); hasCredentials = false; snapshot = nil; devices = []; pending = nil; token = ""; message = "인증과 센서 데이터를 삭제했습니다."; WidgetCenter.shared.reloadAllTimelines() }
        catch { message = "인증 삭제 실패 · 잠금 해제 후 다시 시도해 주세요." }
    }
}
struct SetupView: View {
    @StateObject private var model = SetupModel()
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Label("가습기의 온도와 습도를 한눈에", systemImage: "humidity.fill").font(.headline)
                    Text("서버 없이 이 iPhone에서 LG ThinQ에 직접 연결합니다.").foregroundStyle(.secondary)
                }
                if let snapshot = model.snapshot {
                    Section(snapshot.selection.name) {
                        HStack {
                            Label(Reading.text(snapshot.reading?.temperature, unit: "°C"), systemImage: "thermometer.medium")
                            Spacer()
                            Label(Reading.text(snapshot.reading?.humidity, unit: "%"), systemImage: "humidity.fill").foregroundStyle(.mint)
                        }.font(.title2).monospacedDigit()
                        if let r = snapshot.reading {
                            Text("마지막 수신 \(r.fetchedAt.formatted(date: .abbreviated, time: .shortened))")
                            if r.stale(at: Date()) { Text("오래된 데이터").foregroundStyle(.orange) }
                        } else { Text("수신한 센서 정보 없음") }
                        if model.isDemo { Text("샘플 데이터 · 실제 기기 값이 아닙니다").foregroundStyle(.orange).accessibilityIdentifier("demoNotice") }
                        Button("지금 새로고침") { Task { await model.refresh() } }.disabled(model.busy)
                    }
                }
                if !model.hasCredentials && !model.isDemo {
                    Section("LG 계정 없이 둘러보기") {
                        Button("샘플 데이터로 체험하기") { Task { await model.startDemo() } }.disabled(model.busy).accessibilityIdentifier("startDemo")
                        Text("LG 계정이나 기기 없이 앱과 위젯 화면을 확인합니다. 네트워크를 사용하지 않으며 값은 실제 측정값이 아닙니다.").font(.footnote)
                    }
                }
                Section(model.hasCredentials ? "연결 설정 · 토큰 교체" : "1 · LG 계정 연결") {
                    Link("LG에서 Personal Access Token 발급", destination: URL(string: "https://connect-pat.lgthinq.com")!)
                    Text("ThinQ에 등록한 계정으로 로그인하고 기기 목록·상태 읽기 권한을 허용하세요. PAT는 기기의 Keychain에 저장됩니다.").font(.footnote)
                    SecureField(model.hasCredentials ? "새 PAT (유지하려면 비워 두세요)" : "발급받은 Personal Access Token", text: $model.token).textContentType(.password).autocorrectionDisabled().textInputAutocapitalization(.never)
                    TextField("계정 국가 코드", text: $model.country).textInputAutocapitalization(.characters).autocorrectionDisabled()
                    Picker("계정 서버 지역", selection: $model.region) {
                        Text("한국/아시아 (kic)").tag("kic"); Text("미주 (aic)").tag("aic"); Text("유럽/기타 (eic)").tag("eic")
                    }
                    Button(model.hasCredentials ? "토큰 확인 후 저장 · 기기 찾기" : "연결하고 가습기 찾기") { Task { await model.discover() } }.disabled(model.busy)
                }
                if !model.devices.isEmpty {
                    Section("2 · 가습기 선택") { ForEach(model.devices) { device in Button(device.name) { Task { await model.select(device) } }.disabled(model.busy) } }
                }
                Section {
                    if model.busy { ProgressView() }
                    Text(model.message).font(.footnote).accessibilityIdentifier("connectionStatus")
                }
                Section("홈 화면에 위젯 추가") {
                    Text("홈 화면을 길게 누르고 편집 → 위젯 추가 → Climate Widget을 선택하세요. 작은 크기와 중간 크기를 지원합니다. 모든 위젯은 선택한 가습기 하나를 표시합니다.")
                    Text("30분 간격으로 갱신을 요청하지만 iOS가 실제 시점을 결정합니다. 90분 이상 지난 값은 오래된 데이터로 표시하며, 기기가 제공하지 않는 값은 — 로 표시합니다.").font(.footnote).foregroundStyle(.secondary)
                }
                Section { Button(model.isDemo ? "체험 종료" : "연결 해제 및 데이터 삭제", role: .destructive) { model.disconnect() }.disabled(model.busy).accessibilityIdentifier("disconnect") }
            }.navigationTitle("Climate Widget for ThinQ").navigationBarTitleDisplayMode(.inline).tint(.mint)
        }
    }
}
