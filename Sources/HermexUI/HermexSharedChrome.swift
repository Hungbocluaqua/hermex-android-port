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
}

func HermexSystemImageName(_ name: String) -> String {
#if SKIP
    switch name {
    case "square.and.pencil":
        return "edit"
    case "brain.head.profile":
        return "brain"
    case "calendar.badge.clock":
        return "calendar"
    case "link":
        return "globe"
    case "key.horizontal":
        return "key"
    case "key.fill":
        return "key"
    case "slider.horizontal.3":
        return "slider.horizontal.3"
    case "network":
        return "wifi"
    case "checkmark.circle.fill":
        return "checkmark.circle"
    case "server.rack":
        return "server"
    case "externaldrive.badge.checkmark":
        return "externaldrive"
    case "rectangle.portrait.and.arrow.right":
        return "rectangle.portrait"
    case "point.3.connected.trianglepath.dotted":
        return "point.3.connected.trianglepath"
    default:
        return name
    }
#else
    return name
#endif
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
    public init() {}

    public var body: some View {
#if SKIP
        Text("HERMEX")
            .font(.system(size: 42, weight: .black))
            .foregroundStyle(HermexUIColors.gold)
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .frame(width: HermexLayoutContract.sessionListLogoWidth, alignment: .leading)
            .accessibilityLabel("HERMEX")
#else
        ZStack {
            hermexLogoImage("hermes-fill-mask")
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .foregroundStyle(HermexUIColors.gold)
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
