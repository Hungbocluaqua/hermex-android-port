import SwiftUI
import HermexCore

#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

enum HermexUIColors {
    static let gold = Color(red: 1.0, green: 0.843, blue: 0.0)
    static let darkBackground = Color.black
    // Route backgrounds are supplied by HermexRootScreen so explicit light mode can
    // change the surface without duplicating every screen's background modifier.
    static let systemBackground = Color.clear
    static let secondarySystemBackground = Color.primary.opacity(0.10)
    static let separator = Color.primary.opacity(0.14)
    static let primaryText = Color.primary
    static let secondaryText = Color.secondary.opacity(0.82)
    static let tertiaryText = Color.secondary.opacity(0.58)
    static let glassFill = Color.primary.opacity(0.085)
    static let glassFillStrong = Color.primary.opacity(0.12)
    static let hairline = Color.primary.opacity(0.16)
    static let faintHairline = Color.primary.opacity(0.08)

    static func color(for rawValue: String) -> Color {
        guard let components = HermexAppearanceSettings.rgbComponents(for: rawValue) else {
            return gold
        }

        return Color(
            red: components.red,
            green: components.green,
            blue: components.blue
        )
    }

    static func prefersDarkForeground(for rawValue: String) -> Bool {
        HermexAppearanceSettings.prefersDarkForeground(for: rawValue)
    }
}

private struct HermexGlassEnabledKey: EnvironmentKey {
    static let defaultValue = true
}

extension EnvironmentValues {
    var hermexGlassEnabled: Bool {
        get { self[HermexGlassEnabledKey.self] }
        set { self[HermexGlassEnabledKey.self] = newValue }
    }
}

func HermexSystemImageName(_ name: String) -> String {
#if SKIP
    // Map iOS SF Symbols to the subset SkipUI resolves to Compose vectors.
    switch name {
    case "square.and.pencil", "pencil", "edit":
        return "pencil"
    case "bubble.left.and.bubble.right", "message", "message.fill":
        return "info.circle"
    case "plus", "plus.circle", "plus.circle.fill":
        return "plus"
    case "folder", "folder.fill":
        return "list.bullet"
    case "arrow.triangle.branch", "arrow.triangle.merge":
        return "arrow.forward"
    case "arrow.clockwise", "arrow.counterclockwise", "arrow.triangle.2.circlepath":
        return "arrow.clockwise.circle"
    case "waveform", "mic", "mic.fill":
        return "phone"
    case "arrow.up", "arrow.up.right":
        return "arrow.forward"
    case "paperplane", "paperplane.fill":
        return "paperplane"
    case "stop.fill", "stop", "pause.fill", "xmark", "xmark.circle", "xmark.circle.fill":
        return "xmark"
    case "person.crop.circle", "person.crop.circle.badge.gearshape", "person":
        return "person.crop.circle"
    case "exclamationmark.triangle", "exclamationmark.triangle.fill":
        return "exclamationmark.triangle"
    case "hammer", "hammer.fill", "wrench", "wrench.and.screwdriver":
        return "wrench"
    case "brain.head.profile", "brain", "brain.fill":
        return "info.circle"
    case "chart.bar", "chart.bar.fill", "chart.xyaxis.line":
        return "list.bullet"
    case "calendar.badge.clock", "calendar", "clock":
        return "calendar"
    case "link", "globe":
        return "arrow.forward.square"
    case "key.horizontal", "key.fill", "key":
        return "lock"
    case "slider.horizontal.3":
        return "gearshape"
    case "network", "wifi":
        return "location"
    case "checkmark.circle.fill", "checkmark.circle", "checkmark":
        return "checkmark.circle"
    case "server.rack", "server":
        return "list.bullet"
    case "externaldrive.badge.checkmark", "externaldrive":
        return "list.bullet"
    case "rectangle.portrait.and.arrow.right":
        return "arrow.forward.square"
    case "terminal", "terminal.fill":
        return "arrow.forward.square"
    case "point.3.connected.trianglepath.dotted", "point.3.connected.trianglepath":
        return "arrow.forward"
    case "chevron.left", "chevron.backward":
        return "chevron.left"
    case "chevron.right", "chevron.forward":
        return "chevron.right"
    case "chevron.down":
        return "chevron.down"
    case "chevron.up":
        return "chevron.up"
    case "ellipsis", "ellipsis.circle":
        return "ellipsis"
    case "magnifyingglass":
        return "magnifyingglass"
    case "gearshape", "gearshape.fill":
        return "gearshape"
    case "paperclip":
        return "plus"
    case "sparkles", "sparkle":
        return "star.fill"
    case "photo", "photo.fill":
        return "square.and.arrow.up"
    case "doc", "doc.text", "doc.badge.gearshape":
        return "list.bullet"
    case "checkmark.shield", "checkmark.seal":
        return "checkmark.circle"
    case "questionmark.bubble", "questionmark.circle":
        return "info.circle"
    case "circle.lefthalf.filled":
        return "gearshape"
    case "hand.tap":
        return "hand.thumbsup"
    case "cpu":
        return "wrench"
    case "bell", "bell.fill":
        return "bell"
    case "lock", "lock.fill", "lock.shield.fill":
        return "lock"
    case "pin", "pin.fill":
        return "location"
    case "play", "play.fill":
        return "play"
    case "trash", "trash.fill":
        return "trash"
    case "wifi.slash":
        return "location"
    default:
        return name
    }
#else
    return name
#endif
}

private func hermexAssetName(for systemImage: String) -> String? {
#if SKIP
    switch systemImage {
    case "bubble.left.and.bubble.right", "message", "message.fill":
        return "HermexChatBubbles"
    case "brain", "brain.fill", "brain.head.profile":
        return "LucideBrain"
    case "calendar.badge.clock":
        return "LucideCalendarClock"
    case "chart.bar", "chart.bar.fill", "chart.xyaxis.line":
        return "LucideChartColumnIncreasing"
    case "folder", "folder.fill":
        return "LucideFolder"
    case "hammer", "hammer.fill":
        return "LucideHammer"
    case "person":
        return "LucideUserRound"
    case "person.crop.circle.badge.gearshape":
        return "LucideUserRoundCog"
    case "waveform":
        return "HermexWaveform"
    case "checkmark.shield", "checkmark.seal", "checkmark.circle", "checkmark.circle.fill":
        return "HermexCheckCircle"
    case "link":
        return "HermexLink"
    case "key", "key.fill", "key.horizontal":
        return "HermexKey"
    case "slider.horizontal.3":
        return "HermexSliders"
    case "globe", "network", "wifi":
        return "HermexGlobe"
    case "mic", "mic.fill":
        return "HermexMic"
    case "terminal", "terminal.fill":
        return "HermexTerminal"
    case "rectangle.portrait.and.arrow.right":
        return "HermexLogIn"
    default:
        return nil
    }
#else
    return nil
#endif
}

private func hermexPackageImage(_ name: String) -> Image {
#if SWIFT_PACKAGE
    return Image(name, bundle: .module)
#else
    return Image(name)
#endif
}

struct HermexAssetIcon: View {
    private let name: String
    private let size: CGFloat

    init(name: String, size: CGFloat = 20) {
        self.name = name
        self.size = size
    }

    var body: some View {
        hermexPackageImage(name)
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
    }
}

@ViewBuilder
func HermexIconView(_ systemImage: String, size: CGFloat = 20) -> some View {
#if SKIP
    Image(systemName: HermexSystemImageName(systemImage))
        .font(.system(size: size * 0.82, weight: .semibold))
#else
    if let assetName = hermexAssetName(for: systemImage) {
        HermexAssetIcon(name: assetName, size: size)
    } else {
        Image(systemName: HermexSystemImageName(systemImage))
            .font(.system(size: size * 0.82, weight: .semibold))
    }
#endif
}

public struct HermexMappedLabel: View {
    private let title: String
    private let systemImage: String

    public init(_ title: String, systemImage: String) {
        self.title = title
        self.systemImage = systemImage
    }

    public var body: some View {
        Label {
            Text(title)
        } icon: {
            HermexIconView(systemImage, size: 18)
        }
    }
}

public struct HermexScreenTitle: View {
    private let title: String
    private let subtitle: String?

    public init(_ title: String, subtitle: String? = nil) {
        self.title = title
        self.subtitle = subtitle
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.headline.weight(.semibold))
                .foregroundStyle(HermexUIColors.primaryText)
            if let subtitle, !subtitle.isEmpty {
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(HermexUIColors.secondaryText)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

public struct HermexLogoMark: View {
    private let accent: Color

    public init(accent: Color = Color(red: 1.0, green: 0.843, blue: 0.0)) {
        self.accent = accent
    }

    public var body: some View {
#if SKIP
        HermexSkipWordmark()
            .aspectRatio(HermexLayoutContract.hermexLogoAspectRatio, contentMode: .fit)
            .frame(width: HermexLayoutContract.sessionListLogoWidth)
            .accessibilityLabel("HERMEX")
#else
        ZStack {
            hermexLogoImage("hermes-fill-mask")
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .foregroundStyle(accent)
            hermexLogoImage("hermes-shading-overlay")
                .resizable()
                .scaledToFit()
            hermexLogoImage("hermes-highlight")
                .resizable()
                .scaledToFit()
            hermexLogoImage("hermes-outline-shadow")
                .resizable()
                .scaledToFit()
        }
            .aspectRatio(HermexLayoutContract.hermexLogoAspectRatio, contentMode: .fit)
            .frame(width: HermexLayoutContract.sessionListLogoWidth)
            .accessibilityLabel("HERMEX")
#endif
    }

    private func hermexLogoImage(_ name: String) -> Image {
#if SWIFT_PACKAGE
#if SKIP
        return Image(name + ".png", bundle: .module)
#else
        return Image(name, bundle: .module)
#endif
#else
        return Image(name)
#endif
    }
}

public struct HermexAppIconMark: View {
    private let size: CGFloat

    public init(size: CGFloat = 124) {
        self.size = size
    }

    public var body: some View {
#if SKIP
        HermexSkipAppIconArtwork()
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: size * 0.22, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: size * 0.22, style: .continuous)
                    .stroke(Color.white.opacity(0.22), lineWidth: 1)
            }
            .shadow(color: HermexUIColors.gold.opacity(0.34), radius: 24, y: 10)
            .accessibilityLabel("Hermex")
#else
        appIconImage
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: size * 0.22, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: size * 0.22, style: .continuous)
                    .stroke(Color.white.opacity(0.22), lineWidth: 1)
            }
            .shadow(color: HermexUIColors.gold.opacity(0.34), radius: 24, y: 10)
            .accessibilityLabel("Hermex")
#endif
    }

    private var appIconImage: Image {
#if SWIFT_PACKAGE
#if SKIP
        return Image("HermesAppIcon.png", bundle: .module)
#else
        return Image("HermesAppIcon", bundle: .module)
#endif
#else
        return Image("HermesAppIcon")
#endif
    }
}

#if SKIP
private struct HermexSkipWordmark: View {
    var body: some View {
        Text("HERMEX")
            .font(.system(size: 27, weight: .black, design: .monospaced))
            .foregroundStyle(
                LinearGradient(
                    colors: [HermexUIColors.gold, Color(red: 1.0, green: 0.58, blue: 0.05)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
            .shadow(color: HermexUIColors.gold.opacity(0.32), radius: 5, y: 2)
            .minimumScaleFactor(0.72)
    }
}

private struct HermexSkipAppIconArtwork: View {
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(Color.black)

            Text("H")
                .font(.system(size: 92, weight: .black, design: .rounded))
                .foregroundStyle(
                    LinearGradient(
                        colors: [HermexUIColors.gold, Color(red: 1.0, green: 0.52, blue: 0.03)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .shadow(color: HermexUIColors.gold.opacity(0.45), radius: 7, y: 2)
        }
    }
}
#endif

public struct HermexGlassPanel<Content: View>: View {
    private let content: Content
    private let cornerRadius: CGFloat
    @Environment(\.hermexGlassEnabled) private var glassEnabled
    @Environment(\.colorScheme) private var colorScheme

    public init(cornerRadius: CGFloat = HermexLayoutContract.composerCornerRadiusCollapsed, @ViewBuilder content: () -> Content) {
        self.cornerRadius = cornerRadius
        self.content = content()
    }

    public var body: some View {
        content
            .foregroundStyle(HermexUIColors.primaryText)
            .background(
                glassEnabled ? HermexUIColors.glassFill : opaqueSurface,
                in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            )
            .overlay {
                if glassEnabled {
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [
                                    Color.white.opacity(0.055),
                                    Color.clear,
                                    Color.black.opacity(0.08)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .allowsHitTesting(false)
                }
            }
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(HermexUIColors.hairline, lineWidth: 0.6)
            }
            .shadow(color: Color.black.opacity(0.22), radius: 18, y: 8)
    }

    private var opaqueSurface: Color {
        colorScheme == .dark ? Color.white.opacity(0.12) : Color.black.opacity(0.07)
    }
}

public struct HermexCircleIconButton: View {
    private let systemImage: String
    private let accessibilityLabel: String
    private let size: CGFloat
    private let isFilled: Bool
    private let action: () -> Void
    @Environment(\.hermexGlassEnabled) private var glassEnabled
    @Environment(\.colorScheme) private var colorScheme

    public init(
        systemImage: String,
        accessibilityLabel: String,
        size: CGFloat = HermexLayoutContract.topChromeCircleSize,
        isFilled: Bool = false,
        action: @escaping () -> Void
    ) {
        self.systemImage = systemImage
        self.accessibilityLabel = accessibilityLabel
        self.size = size
        self.isFilled = isFilled
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HermexIconView(systemImage, size: size * 0.34)
                .frame(width: size, height: size)
                .foregroundStyle(isFilled ? Color.black : HermexUIColors.primaryText)
                .background(isFilled ? HermexUIColors.gold : buttonFill, in: Circle())
                .overlay {
                    Circle().stroke(HermexUIColors.hairline, lineWidth: 0.6)
                }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(accessibilityLabel)
    }

    private var buttonFill: Color {
        if glassEnabled {
            return HermexUIColors.glassFillStrong
        }
        return colorScheme == .dark ? Color.white.opacity(0.12) : Color.black.opacity(0.08)
    }
}

public struct HermexIconCluster<Content: View>: View {
    private let content: Content
    @Environment(\.hermexGlassEnabled) private var glassEnabled

    public init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    public var body: some View {
        HStack(spacing: HermexLayoutContract.topChromeClusterSpacing) {
            content
        }
        .background(glassEnabled ? HermexUIColors.glassFill : HermexUIColors.glassFillStrong, in: Capsule())
        .overlay {
            Capsule().stroke(HermexUIColors.hairline, lineWidth: 0.6)
        }
        .clipShape(Capsule())
    }
}

public struct HermexPillLabel: View {
    private let title: String
    private let systemImage: String?
    @Environment(\.hermexGlassEnabled) private var glassEnabled

    public init(_ title: String, systemImage: String? = nil) {
        self.title = title
        self.systemImage = systemImage
    }

    public var body: some View {
        HStack(spacing: 6) {
            if let systemImage {
                HermexIconView(systemImage, size: 17)
            }
            Text(title)
                .lineLimit(1)
            Image(systemName: HermexSystemImageName("chevron.down"))
                .font(.caption2.weight(.semibold))
        }
        .font(.subheadline.weight(.medium))
        .foregroundStyle(HermexUIColors.primaryText)
        .padding(.horizontal, HermexLayoutContract.composerSecondaryBarHorizontalPadding)
        .padding(.vertical, HermexLayoutContract.composerSecondaryBarVerticalPadding)
        .background(glassEnabled ? HermexUIColors.glassFillStrong : HermexUIColors.glassFill, in: Capsule())
        .overlay {
            Capsule().stroke(HermexUIColors.hairline, lineWidth: 0.6)
        }
    }
}

public extension View {
    @ViewBuilder
    func hermexThinMaterialBackground<S: Shape>(in shape: S) -> some View {
        modifier(HermexThinMaterialBackgroundModifier(shape: shape))
    }

    @ViewBuilder
    func hermexUltraThinMaterialBackground<S: Shape>(in shape: S) -> some View {
        modifier(HermexUltraThinMaterialBackgroundModifier(shape: shape))
    }

    @ViewBuilder
    func hermexContentShapeRectangle() -> some View {
#if SKIP
        self
#else
        self.contentShape(Rectangle())
#endif
    }

    @ViewBuilder
    func hermexLayoutPriority(_ value: Double) -> some View {
#if SKIP
        self
#else
        self.layoutPriority(value)
#endif
    }
}

private struct HermexThinMaterialBackgroundModifier<S: Shape>: ViewModifier {
    let shape: S
    @Environment(\.hermexGlassEnabled) private var glassEnabled
    @Environment(\.colorScheme) private var colorScheme

    @ViewBuilder
    func body(content: Content) -> some View {
#if SKIP
        content.background(glassEnabled ? HermexUIColors.glassFillStrong : opaqueSurface, in: shape)
#else
        if glassEnabled {
            content.background(.thinMaterial, in: shape)
        } else {
            content.background(opaqueSurface, in: shape)
        }
#endif
    }

    private var opaqueSurface: Color {
        colorScheme == .dark ? Color.white.opacity(0.12) : Color.black.opacity(0.07)
    }
}

private struct HermexUltraThinMaterialBackgroundModifier<S: Shape>: ViewModifier {
    let shape: S
    @Environment(\.hermexGlassEnabled) private var glassEnabled
    @Environment(\.colorScheme) private var colorScheme

    @ViewBuilder
    func body(content: Content) -> some View {
#if SKIP
        content.background(glassEnabled ? HermexUIColors.glassFill : opaqueSurface, in: shape)
#else
        if glassEnabled {
            content.background(.ultraThinMaterial, in: shape)
        } else {
            content.background(opaqueSurface, in: shape)
        }
#endif
    }

    private var opaqueSurface: Color {
        colorScheme == .dark ? Color.white.opacity(0.09) : Color.black.opacity(0.05)
    }
}
