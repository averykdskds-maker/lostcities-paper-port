#!/usr/bin/env python3
"""Prepare the upstream MIT Lost Cities sources and Modern Tweaks datapack for Paper."""
from __future__ import annotations
import argparse
import shutil
import zipfile
from pathlib import Path

REPLACED = {
    "LostCities.java", "LostCitiesImp.java", "api/LostCityEvent.java",
    "setup/Config.java", "setup/ModSetup.java",
    "worldgen/LostCityFeature.java", "worldgen/LostCitySphereFeature.java",
    "worldgen/lost/cityassets/AssetRegistries.java",
    "worldgen/lost/cityassets/RegistryAssetRegistry.java",
    "worldgen/lost/cityassets/Palette.java",
    "worldgen/ErrorLogger.java", "varia/WorldTools.java",
}
EXCLUDED_DIRS = {"gui", "network", "datagen", "commands", "setup"}

def copy_upstream_sources(upstream: Path, destination: Path) -> None:
    src = upstream / "src/main/java/mcjty/lostcities"
    if not src.is_dir():
        raise SystemExit(f"Missing upstream source directory: {src}")
    target = destination / "mcjty/lostcities"
    shutil.copytree(src, target, dirs_exist_ok=True)
    for directory in EXCLUDED_DIRS:
        p = target / directory
        if p.exists():
            shutil.rmtree(p)
    for relative in REPLACED:
        p = target / relative
        if p.exists():
            p.unlink()

    terrain = target / "worldgen/LostCityTerrainFeature.java"
    text = terrain.read_text(encoding="utf-8")
    text = text.replace("import net.neoforged.neoforge.common.NeoForge;\n", "")
    text = text.replace("NeoForge.EVENT_BUS.post(", "LostCities.post(")
    terrain.write_text(text, encoding="utf-8")

    building_info = target / "worldgen/lost/BuildingInfo.java"
    text = building_info.read_text(encoding="utf-8")
    text = text.replace("import net.neoforged.neoforge.common.NeoForge;\n", "")
    text = text.replace("NeoForge.EVENT_BUS.post(event);", "LostCities.post(event);")
    building_info.write_text(text, encoding="utf-8")

    profile_setup = target / "config/ProfileSetup.java"
    text = profile_setup.read_text(encoding="utf-8")
    text = text.replace("import net.neoforged.fml.loading.FMLPaths;\n", "")
    text = text.replace("import org.apache.commons.io.FileUtils;\n", "")
    text = text.replace("Path path = FMLPaths.CONFIGDIR.get();", "Path path = LostCities.getConfigDir();")
    text = text.replace('FileUtils.readFileToString(file, "UTF-8")',
                        "java.nio.file.Files.readString(file.toPath())")
    profile_setup.write_text(text, encoding="utf-8")

    remnants = []
    for java_file in target.rglob("*.java"):
        body = java_file.read_text(encoding="utf-8")
        if "net.neoforged." in body or "net.minecraftforge." in body or "FMLPaths" in body:
            remnants.append(str(java_file.relative_to(target)))
    if remnants:
        raise SystemExit("Unconverted loader-specific references remain:\n  " + "\n  ".join(remnants))

def copy_port_overlay(project: Path, destination: Path) -> None:
    overlay = project / "src/port/java"
    for file in overlay.rglob("*.java"):
        relative = file.relative_to(overlay)
        out = destination / relative
        out.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(file, out)

def copy_datapack(upstream: Path, tweaks_zip: Path, output: Path) -> None:
    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True)
    base_data = upstream / "src/main/resources/data/lostcities"
    for folder in ("lostcities", "loot_table", "tags"):
        source = base_data / folder
        if source.exists():
            shutil.copytree(source, output / "data/lostcities" / folder, dirs_exist_ok=True)

    with zipfile.ZipFile(tweaks_zip) as archive:
        members = [m for m in archive.namelist() if not m.endswith("/")]
        for member in members:
            if member in ("pack.mcmeta", "pack.png") or member.startswith("data/"):
                target = output / member
                target.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(member) as src, target.open("wb") as dst:
                    shutil.copyfileobj(src, dst)
        try:
            (output / "LICENSE-Modern-Tweaks.txt").write_bytes(archive.read("LICENSE.txt"))
        except KeyError:
            pass

    (output / "LICENSE-LostCities.txt").write_text(
        (upstream / "LICENSE").read_text(encoding="utf-8"), encoding="utf-8")
    if not (output / "pack.mcmeta").exists():
        raise SystemExit("Modern Tweaks pack.mcmeta is missing")
    if not any((output / "data").rglob("*.json")):
        raise SystemExit("No JSON assets were copied into the embedded datapack")

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("upstream", type=Path)
    parser.add_argument("tweaks_zip", type=Path)
    parser.add_argument("project", type=Path)
    args = parser.parse_args()
    project = args.project.resolve()
    generated_source = project / "build/upstream-src"
    generated_resources = project / "build/generated-resources"
    if generated_source.exists(): shutil.rmtree(generated_source)
    if generated_resources.exists(): shutil.rmtree(generated_resources)
    generated_source.mkdir(parents=True)
    generated_resources.mkdir(parents=True)
    copy_upstream_sources(args.upstream.resolve(), generated_source)
    copy_port_overlay(project, generated_source)
    copy_datapack(args.upstream.resolve(), args.tweaks_zip.resolve(),
                  generated_resources / "lostcities_datapack")
    print(f"Prepared {len(list(generated_source.rglob('*.java')))} upstream/adapter Java files and "
          f"{len(list((generated_resources / 'lostcities_datapack/data').rglob('*.json')))} embedded datapack assets.")

if __name__ == "__main__":
    main()
