package procmgr.view;

import procmgr.model.TipoStaff;

public class Tipo {
	
	TipoStaff value;
	String descrip;

	public Tipo(TipoStaff technic, String descrip) {
		this.value=technic;
		this.descrip=descrip;
	}
	
	@Override
	public String toString() {
		return descrip;
	}
	
	public String getDescrip() {
		return descrip;
	}
	
	public TipoStaff getValue() {
		return value;
	}

}
