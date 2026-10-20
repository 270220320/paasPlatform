import importlib.util
from pathlib import Path
skill=Path('C:/Users/meng/.codex/plugins/cache/openai-primary-runtime/documents/26.1015.12040/skills/documents')
spec=importlib.util.spec_from_file_location('doc_renderer',skill/'render_docx.py')
renderer=importlib.util.module_from_spec(spec);spec.loader.exec_module(renderer)
root=Path(__file__).parent
renderer.convert_to_pdf=lambda *args,**kwargs:(str(root/'database.pdf'),'PDF exported by WPS document conversion API')
renderer.rasterize(str(root.parent/'上云2基座数据库用途说明.docx'),str(root/'render'),150,verbose=False,emit_pdf=False)
print('Page PNGs generated')
