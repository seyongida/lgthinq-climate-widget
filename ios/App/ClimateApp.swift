import SwiftUI
import WidgetKit

@main struct ClimateApp: App {
    var body: some Scene { WindowGroup { HomeView().preferredColorScheme(.light) } }
}
enum Theme {
    static let background = Color(RGB(246, 245, 241))
    static let ink = Color(RGB(18, 33, 30))
    static let muted = Color(RGB(98, 113, 107))
    static let accent = Color(RGB(24, 110, 90))
    static let tint = Color(RGB(226, 240, 233))
    static let line = Color(RGB(228, 230, 224))
    static let warning = Color(RGB(158, 88, 45))
    static func gradient(_ comfort: Comfort) -> LinearGradient {
        let colors: [RGB]
        switch comfort {
        case .good: colors = [RGB(11, 62, 56), RGB(24, 118, 96)]
        case .dry: colors = [RGB(112, 58, 20), RGB(170, 98, 32)]
        case .humid: colors = [RGB(16, 56, 104), RGB(44, 102, 164)]
        case .unknown: colors = [RGB(36, 52, 58), RGB(72, 96, 106)]
        }
        return LinearGradient(colors: colors.map { Color($0) }, startPoint: .topLeading, endPoint: .bottomTrailing)
    }
}
enum ActiveSheet: String, Identifiable {
    case credentials, chooser, guide
    var id: String { rawValue }
}
@MainActor final class ClimateModel: ObservableObject {
    @Published var devices: [ThinQDevice] = []
    @Published var states: [String: DeviceState] = [:]
    @Published var catalog: [ThinQDevice] = []
    @Published var connection = "기기를 연결하고 있습니다"
    @Published var credentialStatus = "기기 목록·상태 조회 권한이 있는 토큰을 사용하세요.\n토큰은 이 기기의 Keychain에 보관합니다."
    @Published var notice = ""
    @Published var busy = false
    @Published var hasCredentials = false
    @Published var activeSheet: ActiveSheet?
    @Published var country = "KR"
    @Published var region = "kic"
    private let store = ClimateStore()
    var isDemo: Bool { devices.contains { DemoData.isDemo($0.id) } }
    private var freshReadings: [Reading] { devices.compactMap { states[$0.id]?.reading }.filter { !$0.stale(at: Date()) } }
    var averageHumidity: Double? { Comfort.average(freshReadings.map(\.humidity)) }
    var averageTemperature: Double? { Comfort.average(freshReadings.map(\.temperature)) }
    var comfort: Comfort { Comfort(humidity: averageHumidity) }
    var lastReceived: Date? { freshReadings.map(\.fetchedAt).max() }

    init() {
        do {
            if let config = try store.config() { country = config.country; region = config.region; hasCredentials = true }
        } catch { connection = "연결 설정을 확인해 주세요" }
        reload()
        if !hasCredentials && !isDemo { activeSheet = .credentials }
    }
    func reload() {
        devices = store.devices(); states = store.states(); catalog = store.catalog()
        guard !busy else { return }
        if isDemo { connection = "체험 모드 · 샘플 기기 \(devices.count)개" }
        else if !hasCredentials { connection = "연결 설정을 시작해 주세요" }
        else { connection = devices.isEmpty ? "표시할 기기를 선택해 주세요" : "\(devices.count)개의 기기를 한곳에서" }
    }
    func start() async {
        if hasCredentials && catalog.isEmpty { await discover(choose: false, selectAll: devices.isEmpty) }
        else { await refresh() }
    }
    func refresh() async {
        guard !devices.isEmpty else { return }
        await Refresher.refresh()
        reload()
        WidgetCenter.shared.reloadAllTimelines()
    }
    func requestRefresh() async {
        notice = "갱신 요청 · 1분 이내 요청은 합쳐집니다"
        await refresh()
    }
    func chooseDevices() async {
        guard hasCredentials else { activeSheet = .credentials; return }
        await discover(choose: true, selectAll: false)
    }
    private func discover(choose: Bool, selectAll: Bool) async {
        guard !busy else { return }
        do {
            guard let config = try store.config() else { activeSheet = .credentials; return }
            busy = true; connection = "우리 집 기기를 찾고 있어요…"
            let found = try await ThinQClient(config: config).devices()
            busy = false
            store.setCatalog(found)
            catalog = found
            if found.isEmpty { connection = "지원 기기가 없습니다 · 계정과 권한을 확인해 주세요"; return }
            if choose { reload(); activeSheet = .chooser; return }
            let old = Set(devices.map(\.id))
            apply(found.filter { selectAll || old.contains($0.id) })
            await refresh()
        } catch {
            busy = false
            connection = (error as? ThinQError ?? .network).localizedDescription
        }
    }
    func apply(_ selection: [ThinQDevice]) {
        store.selectDevices(selection)
        reload()
        WidgetCenter.shared.reloadAllTimelines()
    }
    func applyChoice(_ ids: Set<String>) async {
        apply(catalog.filter { ids.contains($0.id) })
        activeSheet = nil
        await refresh()
    }
    func connect(token: String, country: String, region: String) async -> Bool {
        guard !busy else { return false }
        busy = true; defer { busy = false }
        credentialStatus = "계정 연결을 확인하고 있어요…"
        do {
            let old = try? store.config()
            let config = try ThinQConfig(token: token.isEmpty ? old?.token ?? "" : token, country: country, region: region,
                                         clientID: old?.clientID ?? "climate-" + UUID().uuidString)
            let found = try await ThinQClient(config: config).devices()
            guard let lock = StoreLock() else { credentialStatus = "갱신 중입니다. 잠시 후 다시 시도해 주세요."; return false }
            let previous = Set(devices.map(\.id))
            try store.replaceCredentials(config)
            store.setCatalog(found)
            let kept = found.filter { previous.contains($0.id) }
            store.selectDevices(kept.isEmpty ? found : kept)
            withExtendedLifetime(lock) {}
            self.country = config.country; self.region = config.region
            hasCredentials = true
            busy = false
            reload()
            if found.isEmpty { connection = "지원 기기가 없습니다 · 계정과 권한을 확인해 주세요" }
            activeSheet = nil
            WidgetCenter.shared.reloadAllTimelines()
            await refresh()
            return true
        } catch {
            credentialStatus = (error as? ThinQError ?? .network).localizedDescription
            return false
        }
    }
    func startDemo() async {
        guard !hasCredentials else { return }
        do {
            guard let lock = StoreLock() else { credentialStatus = "갱신 중입니다. 잠시 후 다시 시도해 주세요."; return }
            store.selectDevices(DemoData.devices)
            withExtendedLifetime(lock) {}
        }
        reload()
        activeSheet = nil
        await refresh()
    }
    func disconnect() {
        guard !busy, let lock = StoreLock() else { credentialStatus = "갱신 중입니다. 잠시 후 다시 시도해 주세요."; return }
        defer { withExtendedLifetime(lock) {} }
        do {
            try store.clear()
            hasCredentials = false
            notice = ""
            reload()
            credentialStatus = "인증과 센서 데이터를 삭제했습니다."
            WidgetCenter.shared.reloadAllTimelines()
        } catch { credentialStatus = "인증 삭제 실패 · 잠금 해제 후 다시 시도해 주세요." }
    }
}
struct PrimaryStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.system(size: 15, weight: .semibold)).foregroundStyle(.white)
            .frame(maxWidth: .infinity, minHeight: 54)
            .background(Theme.ink.opacity(configuration.isPressed ? 0.8 : 1), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}
struct SecondaryStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.system(size: 15, weight: .semibold)).foregroundStyle(Theme.ink)
            .frame(maxWidth: .infinity, minHeight: 54)
            .background(configuration.isPressed ? Theme.line : Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Theme.line))
    }
}
struct PillStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.system(size: 13, weight: .semibold)).foregroundStyle(Theme.accent)
            .padding(.horizontal, 14).frame(minHeight: 36)
            .background(Theme.tint.opacity(configuration.isPressed ? 0.6 : 1), in: Capsule())
    }
}
private extension View {
    func surfaceCard() -> some View {
        padding(20).frame(maxWidth: .infinity, alignment: .leading)
            .background(.white, in: RoundedRectangle(cornerRadius: 26, style: .continuous))
            .shadow(color: Color(RGB(30, 50, 44)).opacity(0.07), radius: 18, x: 0, y: 8)
    }
}
struct AppMark: View {
    var size: CGFloat = 38
    var body: some View {
        RoundedRectangle(cornerRadius: size * 0.3, style: .continuous).fill(Theme.gradient(.good))
            .frame(width: size, height: size)
            .overlay(Image(systemName: "drop.fill").font(.system(size: size * 0.46, weight: .semibold)).foregroundStyle(.white))
    }
}
struct HomeView: View {
    @StateObject private var model = ClimateModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                header
                hero.padding(.top, 18)
                HStack {
                    Text("나의 기기").font(.system(size: 21, weight: .bold)).foregroundStyle(Theme.ink)
                    Spacer()
                    Button("기기 선택") { Task { await model.chooseDevices() } }.buttonStyle(PillStyle()).disabled(model.busy).accessibilityIdentifier("chooseDevices")
                }.padding(.top, 30)
                VStack(spacing: 14) {
                    if model.devices.isEmpty { emptyCard }
                    ForEach(model.devices) { deviceCard($0) }
                }.padding(.top, 14)
                Button { Task { await model.requestRefresh() } } label: {
                    Label("센서값 새로고침", systemImage: "arrow.clockwise")
                }.buttonStyle(PrimaryStyle()).padding(.top, 16).accessibilityIdentifier("refresh")
                if !model.notice.isEmpty {
                    Text(model.notice).font(.system(size: 12)).foregroundStyle(Theme.muted).frame(maxWidth: .infinity).padding(.top, 10)
                }
                widgetSection.padding(.top, 34)
                Text("30분마다 갱신을 요청하며 iOS 상태에 따라 늦어질 수 있어요. 지원하지 않는 값은 —, 90분 이상 지난 값은 ‘오래됨’으로 표시합니다. 쾌적 구간은 습도 40~60%를 기준으로 한 안내입니다.")
                    .font(.system(size: 12)).foregroundStyle(Theme.muted).lineSpacing(3).padding(.top, 20)
            }
            .padding(EdgeInsets(top: 10, leading: 20, bottom: 36, trailing: 20))
        }
        .background(Theme.background.ignoresSafeArea())
        // Keeps scrolled content from running under the status bar text.
        .safeAreaInset(edge: .top, spacing: 0) { Color.clear.frame(height: 0).background(Theme.background.opacity(0.96).ignoresSafeArea(edges: .top)) }
        .refreshable { await model.requestRefresh() }
        .task { await model.start() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { model.reload(); Task { await model.refresh() } }
        }
        .sheet(item: $model.activeSheet) { sheet in
            switch sheet {
            case .credentials: CredentialsView(model: model)
            case .chooser: DeviceChooser(model: model)
            case .guide: WidgetGuide(model: model)
            }
        }
    }
    private var header: some View {
        HStack(spacing: 10) {
            AppMark()
            VStack(alignment: .leading, spacing: 0) {
                Text("Climate Widget").font(.system(size: 17, weight: .bold)).foregroundStyle(Theme.ink)
                Text("for ThinQ").font(.system(size: 12, weight: .medium)).foregroundStyle(Theme.muted)
            }
            Spacer()
            Button { model.activeSheet = .credentials } label: {
                Image(systemName: "gearshape.fill").font(.system(size: 17)).foregroundStyle(Theme.ink)
                    .frame(width: 44, height: 44).background(.white, in: Circle())
            }
            .accessibilityLabel("연결 설정").accessibilityIdentifier("openSettings")
        }
    }
    private var hero: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("지금 우리 집은").font(.system(size: 15, weight: .medium)).foregroundStyle(.white.opacity(0.85))
                Spacer()
                if model.isDemo {
                    Text("샘플 데이터 · 실제 값이 아닙니다").font(.system(size: 11, weight: .bold)).foregroundStyle(Theme.ink)
                        .padding(.horizontal, 10).padding(.vertical, 5).background(.white, in: Capsule())
                        .accessibilityIdentifier("demoNotice")
                }
            }
            Text(model.comfort.headline).font(.system(size: 31, weight: .bold)).foregroundStyle(.white).padding(.top, 4)
            HStack(alignment: .bottom, spacing: 26) {
                heroMetric(value: model.averageHumidity.map { String(format: "%.0f", $0) } ?? "—", unit: "%", label: "평균 습도", size: 76)
                heroMetric(value: model.averageTemperature.map { String(format: "%.1f°", $0) } ?? "—", unit: "", label: "평균 온도", size: 40)
            }.padding(.top, 14)
            ComfortGauge(humidity: model.averageHumidity, track: .white.opacity(0.22), zone: .white.opacity(0.42), marker: .white).padding(.top, 16)
            Text(heroCaption).font(.system(size: 12, weight: .medium)).foregroundStyle(.white.opacity(0.85)).padding(.top, 12)
        }
        .padding(22)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background {
            ZStack {
                Theme.gradient(model.comfort)
                Circle().fill(.white.opacity(0.07)).frame(width: 260, height: 260).offset(x: 130, y: -90)
                Circle().fill(.white.opacity(0.05)).frame(width: 180, height: 180).offset(x: 170, y: 60)
            }
            .animation(.easeInOut(duration: 0.5), value: model.comfort)
            // clipShape does not clip hit testing, so these circles would otherwise block the buttons around the card.
            .allowsHitTesting(false)
        }
        .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
        .shadow(color: Color(RGB(12, 50, 44)).opacity(0.22), radius: 22, x: 0, y: 12)
    }
    private func heroMetric(value: String, unit: String, label: String, size: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline, spacing: 1) {
                Text(value).font(.system(size: size, weight: .bold, design: .rounded)).monospacedDigit()
                Text(unit).font(.system(size: size * 0.45, weight: .semibold, design: .rounded))
            }.foregroundStyle(.white).lineLimit(1).minimumScaleFactor(0.6)
            Text(label).font(.system(size: 12, weight: .medium)).foregroundStyle(.white.opacity(0.85))
        }
        .accessibilityElement(children: .combine)
    }
    private var heroCaption: String {
        if let date = model.lastReceived { return "\(model.connection) · \(ClimateFormat.time(date)) 수신" }
        return model.connection
    }
    private var emptyCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("아직 선택한 기기가 없어요").font(.system(size: 17, weight: .bold)).foregroundStyle(Theme.ink)
            Text("‘기기 선택’에서 가습기와 공기청정기를 여러 대 고를 수 있어요.").font(.system(size: 14)).foregroundStyle(Theme.muted)
        }.surfaceCard()
    }
    private func deviceCard(_ device: ThinQDevice) -> some View {
        let state = model.states[device.id]
        let reading = state?.reading
        let comfort = Comfort(humidity: reading?.humidity)
        let status = reading.map { ClimateFormat.time($0.fetchedAt) + " 수신" + ($0.stale(at: Date()) ? " · 오래됨" : DemoData.isDemo(device.id) ? " · 샘플" : "") } ?? "센서값을 기다리고 있어요"
        let error = state?.error ?? ""
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                Image(systemName: ClimateFormat.symbol(device)).font(.system(size: 17, weight: .semibold)).foregroundStyle(Theme.accent)
                    .frame(width: 42, height: 42).background(Theme.tint, in: Circle())
                VStack(alignment: .leading, spacing: 1) {
                    Text(device.name).font(.system(size: 17, weight: .bold)).foregroundStyle(Theme.ink).lineLimit(1)
                    Text(device.typeName).font(.system(size: 12)).foregroundStyle(Theme.muted)
                }
                Spacer()
                if comfort != .unknown {
                    Text(comfort.label).font(.system(size: 12, weight: .bold)).foregroundStyle(comfort == .good ? Theme.accent : Theme.warning)
                        .padding(.horizontal, 10).padding(.vertical, 5)
                        .background(comfort == .good ? Theme.tint : Color(RGB(250, 236, 222)), in: Capsule())
                }
            }
            HStack(alignment: .firstTextBaseline) {
                metric("습도", Reading.compact(reading?.humidity, unit: "%"), Theme.accent)
                metric("온도", Reading.compact(reading?.temperature, unit: "°"), Theme.ink)
            }.padding(.top, 18)
            if reading?.humidity != nil {
                ComfortGauge(humidity: reading?.humidity, track: Theme.line, zone: Theme.accent.opacity(0.3), marker: Theme.accent).padding(.top, 14)
            }
            Text(error.isEmpty ? status : error + "\n" + status).font(.system(size: 12)).foregroundStyle(error.isEmpty ? Theme.muted : Theme.warning).padding(.top, 12)
            if let reading, reading.temperature == nil, reading.humidity == nil {
                Text("이 모델은 현재 온도·습도를 제공하지 않습니다").font(.system(size: 12)).foregroundStyle(Theme.muted).padding(.top, 4)
            }
        }.surfaceCard()
    }
    private func metric(_ label: String, _ value: String, _ color: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.system(size: 12, weight: .medium)).foregroundStyle(Theme.muted)
            Text(value).font(.system(size: 32, weight: .bold, design: .rounded)).foregroundStyle(color).lineLimit(1).minimumScaleFactor(0.6).monospacedDigit()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
    private var widgetSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("홈 화면 위젯").font(.system(size: 21, weight: .bold)).foregroundStyle(Theme.ink)
            Text("눌러서 추가 방법과 색상을 확인하세요").font(.system(size: 14)).foregroundStyle(Theme.muted).padding(.top, 4)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 14) {
                    Button { model.activeSheet = .guide } label: {
                        widgetPreview(model, style: WidgetStyle.presets[0], columns: 1, label: "작은 크기 · 기기 1대", width: 158)
                    }.buttonStyle(.plain).accessibilityIdentifier("tileSmall")
                    Button { model.activeSheet = .guide } label: {
                        widgetPreview(model, style: WidgetStyle.presets[1], columns: 2, label: "중간 크기 · 기기 2대", width: 338)
                    }.buttonStyle(.plain)
                }
                .padding(.horizontal, 20).padding(.vertical, 14)
            }
            .padding(.horizontal, -20)
        }
    }
}
@MainActor private func widgetPreview(_ model: ClimateModel, style: WidgetStyle, columns: Int, label: String, width: CGFloat) -> some View {
    let sample = [ThinQDevice(id: "guide-1", name: "거실"), ThinQDevice(id: "guide-2", name: "안방", type: "DEVICE_AIR_PURIFIER")]
    let devices = model.devices.isEmpty ? sample : model.devices
    let states = model.devices.isEmpty ? [
        "guide-1": DeviceState(reading: Reading(temperature: 24.5, humidity: 47, fetchedAt: Date())),
        "guide-2": DeviceState(reading: Reading(temperature: 22, humidity: 53, fetchedAt: Date()))] : model.states
    return VStack(spacing: 8) {
        ClimateWidgetBody(devices: devices, states: states, style: style, now: Date(), columns: columns, interactive: false)
            .frame(width: width, height: 158)
            .background(style.surface, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .shadow(color: Color(RGB(30, 50, 44)).opacity(0.14), radius: 14, x: 0, y: 7)
        Text(label).font(.system(size: 12, weight: .medium)).foregroundStyle(Theme.muted)
    }
}
struct CredentialsView: View {
    @ObservedObject var model: ClimateModel
    @Environment(\.dismiss) private var dismiss
    @State private var token = ""
    @State private var country = ""
    @State private var region = "kic"
    private var onboarding: Bool { !model.hasCredentials && !model.isDemo }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                AppMark(size: 54)
                Text(model.hasCredentials ? "연결 설정" : "우리 집 공기를\n한눈에 확인하세요").font(.system(size: 30, weight: .bold)).foregroundStyle(Theme.ink).padding(.top, 18)
                Text(model.hasCredentials ? "새 토큰으로 언제든 연결을 바꿀 수 있어요. 저장된 토큰은 화면에 표시하지 않습니다." : "LG ThinQ 가습기와 공기청정기의 온도와 습도를 앱과 위젯에서 보여 드려요.")
                    .font(.system(size: 15)).foregroundStyle(Theme.muted).lineSpacing(2).padding(.top, 10)
                if onboarding {
                    VStack(alignment: .leading, spacing: 0) {
                        Text("LG 계정 없이 둘러보기").font(.system(size: 16, weight: .bold)).foregroundStyle(Theme.ink)
                        Text("샘플 값으로 앱과 위젯을 확인합니다. 네트워크를 쓰지 않으며 실제 측정값이 아닙니다.").font(.system(size: 13)).foregroundStyle(Theme.muted).padding(.top, 6)
                        Button("샘플 데이터로 체험하기") { Task { await model.startDemo() } }.buttonStyle(PrimaryStyle()).padding(.top, 14).accessibilityIdentifier("startDemo")
                    }.surfaceCard().padding(.top, 22)
                }
                VStack(alignment: .leading, spacing: 0) {
                    Text(model.hasCredentials ? "토큰 교체" : "내 계정 연결").font(.system(size: 16, weight: .bold)).foregroundStyle(Theme.ink)
                    Link(destination: URL(string: "https://connect-pat.lgthinq.com")!) {
                        HStack {
                            Text("LG에서 토큰 발급하기").font(.system(size: 14, weight: .semibold))
                            Spacer()
                            Image(systemName: "arrow.up.right").font(.system(size: 13, weight: .semibold))
                        }.foregroundStyle(Theme.accent).padding(.horizontal, 14).frame(minHeight: 46).background(Theme.tint, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }.padding(.top, 12)
                    label(model.hasCredentials ? "새 Personal Access Token" : "Personal Access Token")
                    SecureField(model.hasCredentials ? "바꾸려면 새 토큰 입력" : "발급받은 토큰 입력", text: $token)
                        .textContentType(.password).autocorrectionDisabled().textInputAutocapitalization(.never).modifier(FieldStyle())
                    label("LG 계정 국가 코드")
                    TextField("KR", text: $country).autocorrectionDisabled().textInputAutocapitalization(.characters).modifier(FieldStyle())
                    label("LG 계정 서버 지역")
                    Picker("LG 계정 서버 지역", selection: $region) {
                        Text("kic · 한국/아시아").tag("kic"); Text("aic · 미주").tag("aic"); Text("eic · 유럽/기타").tag("eic")
                    }.pickerStyle(.menu).tint(Theme.ink).frame(maxWidth: .infinity, alignment: .leading).modifier(FieldStyle())
                    Text(model.credentialStatus).font(.system(size: 12)).foregroundStyle(Theme.muted).lineSpacing(2).padding(.top, 14).accessibilityIdentifier("credentialStatus")
                    Button(model.hasCredentials ? "연결 확인 후 저장" : "연결하고 시작하기") {
                        Task { if await model.connect(token: token, country: country, region: region) { token = "" } }
                    }.buttonStyle(onboarding ? AnyButtonStyle(SecondaryStyle()) : AnyButtonStyle(PrimaryStyle())).disabled(model.busy).padding(.top, 14).accessibilityIdentifier("connect")
                }.surfaceCard().padding(.top, 14)
                if !onboarding {
                    Button("닫기") { dismiss() }.buttonStyle(SecondaryStyle()).padding(.top, 14).accessibilityIdentifier("closeSettings")
                    Button(model.isDemo ? "체험 종료" : "연결 해제 및 데이터 삭제", role: .destructive) { model.disconnect() }
                        .font(.system(size: 14, weight: .semibold)).foregroundStyle(Theme.warning)
                        .frame(maxWidth: .infinity, minHeight: 50).padding(.top, 4).disabled(model.busy).accessibilityIdentifier("disconnect")
                }
            }
            .padding(EdgeInsets(top: 28, leading: 20, bottom: 28, trailing: 20))
        }
        .background(Theme.background.ignoresSafeArea())
        .scrollDismissesKeyboard(.interactively)
        .interactiveDismissDisabled(onboarding)
        .onAppear { country = model.country; region = model.region }
    }
    private func label(_ text: String) -> some View {
        Text(text).font(.system(size: 13, weight: .semibold)).foregroundStyle(Theme.ink).padding(.top, 16).padding(.bottom, 7)
    }
}
struct AnyButtonStyle: ButtonStyle {
    private let make: (Configuration) -> AnyView
    init<S: ButtonStyle>(_ style: S) { make = { AnyView(style.makeBody(configuration: $0)) } }
    func makeBody(configuration: Configuration) -> some View { make(configuration) }
}
struct FieldStyle: ViewModifier {
    func body(content: Content) -> some View {
        content.font(.system(size: 15)).foregroundStyle(Theme.ink).padding(14)
            .background(Theme.background, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}
struct DeviceChooser: View {
    @ObservedObject var model: ClimateModel
    @State private var selected: Set<String> = []
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                Text("함께 볼 기기").font(.system(size: 27, weight: .bold)).foregroundStyle(Theme.ink)
                Text("앱과 위젯에 표시할 기기를 고르세요.").font(.system(size: 14)).foregroundStyle(Theme.muted).padding(.bottom, 8)
                ForEach(model.catalog) { device in
                    Button {
                        if selected.contains(device.id) { selected.remove(device.id) } else { selected.insert(device.id) }
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: ClimateFormat.symbol(device)).font(.system(size: 16, weight: .semibold)).foregroundStyle(Theme.accent)
                                .frame(width: 40, height: 40).background(Theme.tint, in: Circle())
                            VStack(alignment: .leading, spacing: 1) {
                                Text(device.name).font(.system(size: 16, weight: .semibold)).foregroundStyle(Theme.ink).lineLimit(1)
                                Text(device.typeName).font(.system(size: 12)).foregroundStyle(Theme.muted)
                            }
                            Spacer()
                            Image(systemName: selected.contains(device.id) ? "checkmark.circle.fill" : "circle")
                                .font(.system(size: 24)).foregroundStyle(selected.contains(device.id) ? Theme.accent : Theme.line)
                        }
                        .padding(14).background(.white, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                    }.buttonStyle(.plain)
                    .accessibilityAddTraits(selected.contains(device.id) ? .isSelected : [])
                }
                Button("선택 완료") { Task { await model.applyChoice(selected) } }.buttonStyle(PrimaryStyle()).padding(.top, 8)
                Button("취소") { model.activeSheet = nil }.buttonStyle(SecondaryStyle())
            }
            .padding(EdgeInsets(top: 28, leading: 20, bottom: 28, trailing: 20))
        }
        .background(Theme.background.ignoresSafeArea())
        .onAppear { selected = Set(model.devices.map(\.id)) }
    }
}
struct WidgetGuide: View {
    @ObservedObject var model: ClimateModel
    @Environment(\.dismiss) private var dismiss
    private let steps = ["홈 화면의 빈 곳을 길게 누릅니다.", "왼쪽 위 편집 → 위젯 추가를 누릅니다.", "Climate Widget을 찾아 크기를 고릅니다.", "추가한 위젯을 길게 눌러 위젯 편집에서 기기와 색상을 바꿉니다."]
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text("위젯 추가하기").font(.system(size: 27, weight: .bold)).foregroundStyle(Theme.ink)
                    Spacer()
                    Button("닫기") { dismiss() }.buttonStyle(PillStyle()).accessibilityIdentifier("closeGuide")
                }
                VStack(alignment: .leading, spacing: 14) {
                    ForEach(steps.indices, id: \.self) { index in
                        HStack(alignment: .top, spacing: 12) {
                            Text("\(index + 1)").font(.system(size: 13, weight: .bold, design: .rounded)).foregroundStyle(Theme.accent)
                                .frame(width: 28, height: 28).background(Theme.tint, in: Circle())
                            Text(steps[index]).font(.system(size: 15)).foregroundStyle(Theme.ink).padding(.top, 3).fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }.surfaceCard().padding(.top, 18)
                Text("위젯의 시각을 누르면 새로고침되고, 나머지 영역을 누르면 앱이 열려요. 아이폰 홈 화면의 가장 작은 위젯은 ‘작은 크기’이며, 더 작게 보려면 잠금 화면 위젯을 쓰세요. 잠금 화면을 길게 눌러 사용자화에서 추가합니다.")
                    .font(.system(size: 13)).foregroundStyle(Theme.muted).lineSpacing(2).padding(.top, 14)
                Text("색상").font(.system(size: 21, weight: .bold)).foregroundStyle(Theme.ink).padding(.top, 28)
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 14), GridItem(.flexible())], spacing: 18) {
                    ForEach(WidgetStyle.names.indices, id: \.self) { index in
                        widgetPreview(model, style: WidgetStyle.presets[index], columns: 1, label: WidgetStyle.names[index], width: 158)
                    }
                }.padding(.top, 14)
            }
            .padding(EdgeInsets(top: 28, leading: 20, bottom: 28, trailing: 20))
        }
        .background(Theme.background.ignoresSafeArea())
    }
}
