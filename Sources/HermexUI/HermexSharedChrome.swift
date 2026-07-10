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
    static let systemBackground = Color.black
    static let secondarySystemBackground = Color.white.opacity(0.10)
    static let separator = Color.white.opacity(0.14)
    static let primaryText = Color.white
    static let secondaryText = Color.white.opacity(0.58)
    static let tertiaryText = Color.white.opacity(0.38)
    static let glassFill = Color.white.opacity(0.085)
    static let glassFillStrong = Color.white.opacity(0.12)
    static let hairline = Color.white.opacity(0.16)
    static let faintHairline = Color.white.opacity(0.08)

    static func color(for rawValue: String) -> Color {
        guard let hex = HermexAppearanceSettings.normalizedHex(rawValue),
              let value = UInt32(String(hex.dropFirst()), radix: 16)
        else {
            return gold
        }

        return Color(
            red: Double((value & 0xFF0000) >> 16) / 255.0,
            green: Double((value & 0x00FF00) >> 8) / 255.0,
            blue: Double(value & 0x0000FF) / 255.0
        )
    }

    static func prefersDarkForeground(for rawValue: String) -> Bool {
        HermexAppearanceSettings.prefersDarkForeground(for: rawValue)
    }
}

func HermexSystemImageName(_ name: String) -> String {
#if SKIP
    // Map iOS SF Symbols to Material-ish Skip names. Prefer icons that remain
    // visually distinct so utility rails and chat chrome do not collapse to one glyph.
    switch name {
    case "square.and.pencil", "pencil", "edit":
        return "edit"
    case "plus", "plus.circle", "plus.circle.fill":
        return "plus"
    case "folder", "folder.fill":
        return "folder"
    case "arrow.triangle.branch", "arrow.triangle.merge":
        return "arrow.triangle.branch"
    case "arrow.clockwise", "arrow.counterclockwise", "arrow.triangle.2.circlepath":
        return "arrow.clockwise"
    case "waveform", "mic", "mic.fill":
        return "mic"
    case "arrow.up", "paperplane", "paperplane.fill":
        return "arrow.up"
    case "stop.fill", "stop", "xmark", "xmark.circle", "xmark.circle.fill":
        return "xmark"
    case "person.crop.circle", "person.crop.circle.badge.gearshape", "person":
        return "person.crop.circle"
    case "exclamationmark.triangle", "exclamationmark.triangle.fill":
        return "exclamationmark.triangle"
    case "hammer", "hammer.fill", "wrench", "wrench.and.screwdriver":
        return "hammer"
    case "brain.head.profile", "brain", "brain.fill":
        return "brain"
    case "chart.bar", "chart.bar.fill", "chart.xyaxis.line":
        return "chart.bar"
    case "calendar.badge.clock", "calendar", "clock":
        return "calendar"
    case "link", "globe":
        return "link"
    case "key.horizontal", "key.fill", "key":
        return "key"
    case "slider.horizontal.3":
        return "slider.horizontal.3"
    case "network", "wifi":
        return "wifi"
    case "checkmark.circle.fill", "checkmark.circle", "checkmark":
        return "checkmark.circle"
    case "server.rack", "server":
        return "server.rack"
    case "externaldrive.badge.checkmark", "externaldrive":
        return "externaldrive"
    case "rectangle.portrait.and.arrow.right":
        return "rectangle.portrait.and.arrow.right"
    case "point.3.connected.trianglepath.dotted", "point.3.connected.trianglepath":
        return "point.3.connected.trianglepath"
    case "chevron.left", "chevron.backward":
        return "chevron.left"
    case "chevron.right", "chevron.forward":
        return "chevron.right"
    case "chevron.down":
        return "chevron.down"
    case "chevron.up":
        return "chevron.up"
    case "ellipsis":
        return "ellipsis"
    case "magnifyingglass":
        return "magnifyingglass"
    case "gearshape", "gearshape.fill":
        return "gearshape"
    case "paperclip":
        return "paperclip"
    case "sparkles", "sparkle":
        return "sparkles"
    case "photo", "photo.fill":
        return "photo"
    case "doc", "doc.text", "doc.badge.gearshape":
        return "doc"
    case "checkmark.shield", "checkmark.seal":
        return "checkmark.circle"
    case "questionmark.bubble", "questionmark.circle":
        return "questionmark.circle"
    case "circle.lefthalf.filled":
        return "circle.lefthalf.filled"
    case "hand.tap":
        return "hand.tap"
    case "cpu":
        return "cpu"
    case "bell", "bell.fill":
        return "bell"
    case "lock", "lock.fill", "lock.shield.fill":
        return "lock"
    case "pin", "pin.fill":
        return "pin"
    default:
        return name
    }
#else
    return name
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
            Image(systemName: HermexSystemImageName(systemImage))
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
    }

    private func hermexLogoImage(_ name: String) -> Image {
#if SWIFT_PACKAGE
        return Image(name, bundle: .module)
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
    }

    private var appIconImage: Image {
#if SWIFT_PACKAGE
        return Image("HermesAppIcon", bundle: .module)
#else
        return Image("HermesAppIcon")
#endif
    }
}

public struct HermexGlassPanel<Content: View>: View {
    private let content: Content
    private let cornerRadius: CGFloat

    public init(cornerRadius: CGFloat = HermexLayoutContract.composerCornerRadiusCollapsed, @ViewBuilder content: () -> Content) {
        self.cornerRadius = cornerRadius
        self.content = content()
    }

    public var body: some View {
        content
            .foregroundStyle(HermexUIColors.primaryText)
            .background(
                HermexUIColors.glassFill,
                in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            )
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(HermexUIColors.hairline, lineWidth: 0.6)
            }
            .shadow(color: Color.black.opacity(0.22), radius: 18, y: 8)
    }
}

public struct HermexCircleIconButton: View {
    private let systemImage: String
    private let accessibilityLabel: String
    private let size: CGFloat
    private let isFilled: Bool
    private let action: () -> Void

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
            Image(systemName: HermexSystemImageName(systemImage))
                .font(.system(size: size * 0.34, weight: .semibold))
                .frame(width: size, height: size)
                .foregroundStyle(isFilled ? Color.black : HermexUIColors.primaryText)
                .background(isFilled ? HermexUIColors.gold : HermexUIColors.glassFillStrong, in: Circle())
                .overlay {
                    Circle().stroke(HermexUIColors.hairline, lineWidth: 0.6)
                }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(accessibilityLabel)
    }
}

public struct HermexIconCluster<Content: View>: View {
    private let content: Content

    public init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    public var body: some View {
        HStack(spacing: HermexLayoutContract.topChromeClusterSpacing) {
            content
        }
        .background(HermexUIColors.glassFill, in: Capsule())
        .overlay {
            Capsule().stroke(HermexUIColors.hairline, lineWidth: 0.6)
        }
        .clipShape(Capsule())
    }
}

public struct HermexPillLabel: View {
    private let title: String
    private let systemImage: String?

    public init(_ title: String, systemImage: String? = nil) {
        self.title = title
        self.systemImage = systemImage
    }

    public var body: some View {
        HStack(spacing: 6) {
            if let systemImage {
                Image(systemName: HermexSystemImageName(systemImage))
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
        .background(HermexUIColors.glassFillStrong, in: Capsule())
        .overlay {
            Capsule().stroke(HermexUIColors.hairline, lineWidth: 0.6)
        }
    }
}

public extension View {
    @ViewBuilder
    func hermexThinMaterialBackground<S: Shape>(in shape: S) -> some View {
#if SKIP
        self.background(HermexUIColors.glassFillStrong, in: shape)
#else
        self.background(.thinMaterial, in: shape)
#endif
    }

    @ViewBuilder
    func hermexUltraThinMaterialBackground<S: Shape>(in shape: S) -> some View {
#if SKIP
        self.background(HermexUIColors.glassFill, in: shape)
#else
        self.background(.ultraThinMaterial, in: shape)
#endif
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
