import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from main import procesar  # noqa: E402


class ProcesadorTests(unittest.TestCase):
    def test_calcula_porcentaje_y_conserva_ganador(self):
        resultado = procesar({
            "rifa_codigo": "ABC123",
            "titulo": "Rifa de prueba",
            "cantidad_boletos": 200,
            "boletos_vendidos": 150,
            "ganadores": [{"premio": "Moto", "numero": 42}],
        })
        self.assertEqual(resultado["porcentaje_vendido"], 75.0)
        self.assertEqual(resultado["ganadores"][0]["numero"], 42)
        self.assertIn("procesado_en", resultado)

    def test_rechaza_codigo_invalido(self):
        with self.assertRaises(ValueError):
            procesar({"rifa_codigo": "../escape", "cantidad_boletos": 10, "boletos_vendidos": 1})

    def test_rechaza_numeros_inconsistentes(self):
        with self.assertRaises(ValueError):
            procesar({"rifa_codigo": "OK1", "cantidad_boletos": 10, "boletos_vendidos": 11})

    def test_rechaza_ganador_fuera_de_rango(self):
        with self.assertRaises(ValueError):
            procesar({"rifa_codigo": "OK1", "cantidad_boletos": 10, "boletos_vendidos": 2,
                      "ganadores": [{"premio": "Bono", "numero": 10}]})


if __name__ == "__main__":
    unittest.main()
